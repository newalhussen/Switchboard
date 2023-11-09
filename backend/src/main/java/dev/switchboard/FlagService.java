package dev.switchboard;

import dev.switchboard.FlagRepository.EnvRow;
import dev.switchboard.FlagRepository.FlagRow;
import dev.switchboard.Models.AuditEntry;
import dev.switchboard.Models.CreateFlag;
import dev.switchboard.Models.EnvironmentSettings;
import dev.switchboard.Models.Evaluation;
import dev.switchboard.Models.Flag;
import dev.switchboard.Models.NewKey;
import dev.switchboard.Models.UpdateEnvironment;
import dev.switchboard.Models.UpdateFlag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlagService {

    private final FlagRepository repo;

    public FlagService(FlagRepository repo) {
        this.repo = repo;
    }

    private Flag toFlag(FlagRow f, Map<String, EnvironmentSettings> settings) {
        Map<String, EnvironmentSettings> envs = new LinkedHashMap<>();
        for (EnvRow e : repo.environments()) {
            envs.put(e.name(), settings != null && settings.containsKey(e.name()) ? settings.get(e.name()) : new EnvironmentSettings(false, 100, null));
        }
        return new Flag(f.key(), f.name(), f.description(), f.createdAt(), f.updatedAt(), envs);
    }

    @Transactional(readOnly = true)
    public List<Flag> list() {
        var all = repo.settingsByFlag();
        return repo.listFlags().stream().map(f -> toFlag(f, all.get(f.id()))).toList();
    }

    @Transactional(readOnly = true)
    public Flag get(String key) {
        FlagRow f = repo.findFlag(key).orElseThrow(() -> new NotFoundException("Flag not found: " + key));
        return toFlag(f, repo.settingsByFlag().get(f.id()));
    }

    @Transactional
    public Flag create(CreateFlag req, String actor) {
        try {
            repo.insertFlag(req.key(), req.name().trim(), req.description());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("A flag with key '" + req.key() + "' already exists");
        }
        repo.audit(req.key(), null, "CREATED", actor, "Created flag \"" + req.name().trim() + "\" (all environments off)");
        return get(req.key());
    }

    @Transactional
    public Flag update(String key, UpdateFlag req, String actor) {
        FlagRow f = repo.findFlag(key).orElseThrow(() -> new NotFoundException("Flag not found: " + key));
        repo.updateFlag(f.id(), req.name() == null ? null : req.name().trim(), req.description());
        repo.audit(key, null, "UPDATED", actor, "Updated name/description");
        return get(key);
    }

    @Transactional
    public void delete(String key, String actor) {
        FlagRow f = repo.findFlag(key).orElseThrow(() -> new NotFoundException("Flag not found: " + key));
        repo.deleteFlag(f.id());
        repo.audit(key, null, "DELETED", actor, "Deleted flag \"" + f.name() + "\"");
    }

    @Transactional
    public Flag setEnvironment(String key, String env, UpdateEnvironment req, String actor) {
        if (req.enabled() == null && req.rolloutPercentage() == null) throw new BadRequestException("Provide enabled and/or rolloutPercentage");
        FlagRow f = repo.findFlag(key).orElseThrow(() -> new NotFoundException("Flag not found: " + key));
        EnvRow e = repo.environments().stream().filter(x -> x.name().equals(env)).findFirst()
                .orElseThrow(() -> new NotFoundException("Unknown environment: " + env));
        EnvironmentSettings before = repo.settings(f.id(), e.id()).orElse(new EnvironmentSettings(false, 100, null));
        boolean enabled = req.enabled() != null ? req.enabled() : before.enabled();
        int pct = req.rolloutPercentage() != null ? req.rolloutPercentage() : before.rolloutPercentage();
        if (enabled == before.enabled() && pct == before.rolloutPercentage() && before.updatedAt() != null) return get(key);
        repo.upsertSettings(f.id(), e.id(), enabled, pct);
        String action = enabled != before.enabled() ? (enabled ? "ENABLED" : "DISABLED") : "ROLLOUT_CHANGED";
        repo.audit(key, env, action, actor, "enabled " + before.enabled() + " → " + enabled + ", rollout " + before.rolloutPercentage() + "% → " + pct + "%");
        return get(key);
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> audit(String key, int limit) {
        return repo.audit(key, Math.min(Math.max(limit, 1), 500));
    }

    @Transactional
    public NewKey rotateKey(String env, String actor) {
        String key = Keys.generate(env);
        if (repo.setKey(env, Keys.hash(key), Keys.prefix(key)) == 0) throw new NotFoundException("Unknown environment: " + env);
        repo.audit("*", env, "KEY_ROTATED", actor, "SDK key rotated; the previous key no longer works");
        return new NewKey(env, key);
    }

    /* ---- evaluation ---- */

    @Transactional(readOnly = true)
    public Evaluation evaluate(String env, String flagKey, String userId) {
        FlagRow f = repo.findFlag(flagKey).orElseThrow(() -> new NotFoundException("Flag not found: " + flagKey));
        EnvRow e = repo.environments().stream().filter(x -> x.name().equals(env)).findFirst().orElseThrow();
        EnvironmentSettings s = repo.settings(f.id(), e.id()).orElse(new EnvironmentSettings(false, 100, null));
        Evaluator.Result r = Evaluator.evaluate(flagKey, s.enabled(), s.rolloutPercentage(), userId);
        return new Evaluation(flagKey, r.value(), r.reason().name(), env);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> evaluateAll(String env, String userId) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        repo.evaluationSettings(env).forEach((k, s) -> out.put(k, Evaluator.evaluate(k, s.enabled(), s.rolloutPercentage(), userId).value()));
        return out;
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String m) { super(m); }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String m) { super(m); }
    }
}
