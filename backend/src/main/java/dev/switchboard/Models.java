package dev.switchboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response shapes of the REST API. */
public final class Models {
    private Models() {}

    public record EnvironmentSettings(boolean enabled, int rolloutPercentage, Instant updatedAt) {}

    public record Flag(String key, String name, String description, Instant createdAt, Instant updatedAt,
                       Map<String, EnvironmentSettings> environments) {}

    public record CreateFlag(
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{0,99}$", message = "Use lowercase letters, digits, dots, dashes or underscores (max 100)") String key,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {}

    public record UpdateFlag(@Size(min = 1, max = 120) String name, @Size(max = 500) String description) {}

    public record UpdateEnvironment(Boolean enabled, @Min(0) @Max(100) Integer rolloutPercentage) {}

    public record EnvironmentInfo(String name, String keyPrefix) {}

    public record NewKey(String environment, String sdkKey) {}

    public record AuditEntry(long id, Instant at, String flagKey, String environment, String action, String actor, String detail) {}

    public record EvaluateRequest(@NotBlank @Size(max = 100) String flagKey, @Size(max = 200) String userId) {}

    public record Evaluation(String flagKey, boolean value, String reason, String environment) {}

    public record EvaluateAll(String environment, Map<String, Boolean> flags) {}

    public record ApiError(String error, Map<String, String> fields, List<String> details) {}
}
