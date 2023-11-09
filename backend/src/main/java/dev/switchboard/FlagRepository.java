package dev.switchboard;

import dev.switchboard.Models.AuditEntry;
import dev.switchboard.Models.EnvironmentInfo;
import dev.switchboard.Models.EnvironmentSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FlagRepository {

    public record FlagRow(long id, String key, String name, String description, Instant createdAt, Instant updatedAt) {}

    public record EnvRow(long id, String name) {}

    private final JdbcClient jdbc;

    public FlagRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant ts(Object o) {
        return o == null ? null : ((Timestamp) o).toInstant();
    }

    private static FlagRow flag(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        return new FlagRow(r.getLong("id"), r.getString("flag_key"), r.getString("name"), r.getString("description"),
                ts(r.getTimestamp("created_at")), ts(r.getTimestamp("updated_at")));
    }

    public List<FlagRow> listFlags() {
        return jdbc.sql("SELECT * FROM flag ORDER BY flag_key").query(FlagRepository::flag).list();
    }

    public Optional<FlagRow> findFlag(String key) {
        return jdbc.sql("SELECT * FROM flag WHERE flag_key = :k").param("k", key).query(FlagRepository::flag).optional();
    }

    public FlagRow insertFlag(String key, String name, String description) {
        return jdbc.sql("INSERT INTO flag (flag_key, name, description) VALUES (:k, :n, :d) RETURNING *")
                .param("k", key).param("n", name).param("d", description == null ? "" : description)
                .query(FlagRepository::flag).single();
    }

    public void updateFlag(long id, String name, String description) {
        jdbc.sql("UPDATE flag SET name = COALESCE(:n, name), description = COALESCE(:d, description), updated_at = now() WHERE id = :id")
                .param("n", name).param("d", description).param("id", id).update();
    }

    public void deleteFlag(long id) {
        jdbc.sql("DELETE FROM flag WHERE id = :id").param("id", id).update();
    }

    public List<EnvRow> environments() {
        return jdbc.sql("SELECT id, name FROM environment ORDER BY id").query((r, n) -> new EnvRow(r.getLong("id"), r.getString("name"))).list();
    }

    public List<EnvironmentInfo> environmentInfo() {
        return jdbc.sql("SELECT name, sdk_key_prefix FROM environment ORDER BY id")
                .query((r, n) -> new EnvironmentInfo(r.getString("name"), r.getString("sdk_key_prefix"))).list();
    }

    public boolean hasEnvironment(String name) {
        return jdbc.sql("SELECT count(*) FROM environment WHERE name = :n").param("n", name).query(Long.class).single() > 0;
    }

    public void createEnvironment(String name, String keyHash, String prefix) {
        jdbc.sql("INSERT INTO environment (name, sdk_key_hash, sdk_key_prefix) VALUES (:n, :h, :p)")
                .param("n", name).param("h", keyHash).param("p", prefix).update();
    }

    public int setKey(String env, String keyHash, String prefix) {
        return jdbc.sql("UPDATE environment SET sdk_key_hash = :h, sdk_key_prefix = :p WHERE name = :n")
                .param("h", keyHash).param("p", prefix).param("n", env).update();
    }

    public Optional<String> environmentForKeyHash(String hash) {
        return jdbc.sql("SELECT name FROM environment WHERE sdk_key_hash = :h").param("h", hash).query(String.class).optional();
    }

    /** Settings of every flag in every environment, keyed by flag id then environment name. */
    public Map<Long, Map<String, EnvironmentSettings>> settingsByFlag() {
        Map<Long, Map<String, EnvironmentSettings>> out = new LinkedHashMap<>();
        jdbc.sql("SELECT fe.flag_id, e.name, fe.enabled, fe.rollout_percentage, fe.updated_at FROM flag_environment fe JOIN environment e ON e.id = fe.environment_id ORDER BY e.id")
                .query((r, n) -> {
                    out.computeIfAbsent(r.getLong("flag_id"), k -> new LinkedHashMap<>()).put(r.getString("name"),
                            new EnvironmentSettings(r.getBoolean("enabled"), r.getInt("rollout_percentage"), ts(r.getTimestamp("updated_at"))));
                    return null;
                }).list();
        return out;
    }

    public Optional<EnvironmentSettings> settings(long flagId, long envId) {
        return jdbc.sql("SELECT enabled, rollout_percentage, updated_at FROM flag_environment WHERE flag_id = :f AND environment_id = :e")
                .param("f", flagId).param("e", envId)
                .query((r, n) -> new EnvironmentSettings(r.getBoolean("enabled"), r.getInt("rollout_percentage"), ts(r.getTimestamp("updated_at")))).optional();
    }

    public void upsertSettings(long flagId, long envId, boolean enabled, int pct) {
        jdbc.sql("""
                INSERT INTO flag_environment (flag_id, environment_id, enabled, rollout_percentage) VALUES (:f, :e, :en, :p)
                ON CONFLICT (flag_id, environment_id) DO UPDATE SET enabled = :en, rollout_percentage = :p, updated_at = now()""")
                .param("f", flagId).param("e", envId).param("en", enabled).param("p", pct).update();
        jdbc.sql("UPDATE flag SET updated_at = now() WHERE id = :f").param("f", flagId).update();
    }

    public void audit(String flagKey, String env, String action, String actor, String detail) {
        jdbc.sql("INSERT INTO audit_log (flag_key, environment, action, actor, detail) VALUES (:f, :e, :a, :c, :d)")
                .param("f", flagKey).param("e", env).param("a", action).param("c", actor).param("d", detail).update();
    }

    public List<AuditEntry> audit(String flagKey, int limit) {
        String sql = "SELECT * FROM audit_log " + (flagKey == null ? "" : "WHERE flag_key = :f ") + "ORDER BY at DESC, id DESC LIMIT :l";
        var q = jdbc.sql(sql).param("l", limit);
        if (flagKey != null) q = q.param("f", flagKey);
        return q.query((r, n) -> new AuditEntry(r.getLong("id"), ts(r.getTimestamp("at")), r.getString("flag_key"), r.getString("environment"),
                r.getString("action"), r.getString("actor"), r.getString("detail"))).list();
    }

    /** Raw settings for evaluation of one environment: flag key -> settings. */
    public Map<String, EnvironmentSettings> evaluationSettings(String env) {
        Map<String, EnvironmentSettings> out = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT f.flag_key, fe.enabled, fe.rollout_percentage, fe.updated_at FROM flag f
                LEFT JOIN environment e ON e.name = :env
                LEFT JOIN flag_environment fe ON fe.flag_id = f.id AND fe.environment_id = e.id ORDER BY f.flag_key""")
                .param("env", env)
                .query((r, n) -> {
                    out.put(r.getString("flag_key"), new EnvironmentSettings(r.getBoolean("enabled"), r.getObject("rollout_percentage") == null ? 100 : r.getInt("rollout_percentage"), ts(r.getTimestamp("updated_at"))));
                    return null;
                }).list();
        return out;
    }
}
