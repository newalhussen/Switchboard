package dev.switchboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** End to end against a scratch PostgreSQL database (DB_URL, default switchboard_test on localhost:5434). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5434/switchboard_test}",
        "spring.datasource.username=${TEST_DB_USER:postgres}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "switchboard.admin-token=test-admin-token-123456",
})
class ApiIT {
    static final String ADMIN = "test-admin-token-123456";

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient http = HttpClient.newHttpClient();

    HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void fullFlow() throws Exception {
        // authentication
        assertThat(call("GET", "/api/v1/flags", null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/flags", "wrong", null).statusCode()).isEqualTo(401);

        // create + validation + duplicate
        assertThat(call("POST", "/api/v1/flags", ADMIN, "{\"key\":\"Bad Key\",\"name\":\"x\"}").statusCode()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/flags", ADMIN, "{\"key\":\"new-checkout\",\"name\":\"New checkout\"}").statusCode()).isEqualTo(201);
        assertThat(call("POST", "/api/v1/flags", ADMIN, "{\"key\":\"new-checkout\",\"name\":\"dup\"}").statusCode()).isEqualTo(409);
        assertThat(call("PUT", "/api/v1/flags/new-checkout/environments/production", ADMIN, "{\"rolloutPercentage\":150}").statusCode()).isEqualTo(400);

        // an SDK key for development (rotate returns it once)
        String keyJson = call("POST", "/api/v1/environments/development/rotate-key", ADMIN, null).body();
        String sdk = keyJson.replaceAll(".*\"sdkKey\":\"([^\"]+)\".*", "$1");
        assertThat(sdk).startsWith("sb_development_");

        // an SDK key cannot manage flags; the admin token cannot evaluate
        assertThat(call("GET", "/api/v1/flags", sdk, null).statusCode()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/evaluate", ADMIN, "{\"flagKey\":\"new-checkout\",\"userId\":\"u1\"}").statusCode()).isEqualTo(403);

        // flags start off
        var off = call("POST", "/api/v1/evaluate", sdk, "{\"flagKey\":\"new-checkout\",\"userId\":\"u1\"}");
        assertThat(off.body()).contains("\"value\":false").contains("DISABLED");

        // enable at 100% in development only
        assertThat(call("PUT", "/api/v1/flags/new-checkout/environments/development", ADMIN, "{\"enabled\":true}").statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/evaluate", sdk, "{\"flagKey\":\"new-checkout\",\"userId\":\"u1\"}").body()).contains("\"value\":true");

        // 0% rollout turns everyone off; production stays untouched
        call("PUT", "/api/v1/flags/new-checkout/environments/development", ADMIN, "{\"rolloutPercentage\":0}");
        assertThat(call("POST", "/api/v1/evaluate", sdk, "{\"flagKey\":\"new-checkout\",\"userId\":\"u1\"}").body()).contains("\"value\":false");
        assertThat(call("GET", "/api/v1/evaluate?userId=u1", sdk, null).body()).contains("\"new-checkout\":false");

        // unknown flag
        assertThat(call("POST", "/api/v1/evaluate", sdk, "{\"flagKey\":\"nope\",\"userId\":\"u1\"}").statusCode()).isEqualTo(404);

        // audit trail recorded each change, newest first
        String audit = call("GET", "/api/v1/flags/new-checkout/audit", ADMIN, null).body();
        assertThat(audit).contains("CREATED").contains("ENABLED").contains("ROLLOUT_CHANGED");
        assertThat(audit.indexOf("ROLLOUT_CHANGED")).isLessThan(audit.indexOf("CREATED"));

        // rotating invalidates the old key
        String newKey = call("POST", "/api/v1/environments/development/rotate-key", ADMIN, null).body().replaceAll(".*\"sdkKey\":\"([^\"]+)\".*", "$1");
        assertThat(call("GET", "/api/v1/evaluate", sdk, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/evaluate", newKey, null).statusCode()).isEqualTo(200);

        // keys are stored hashed, never in clear
        assertThat(jdbc.queryForObject("SELECT count(*) FROM environment WHERE sdk_key_hash = ?", Integer.class, newKey)).isZero();
    }
}
