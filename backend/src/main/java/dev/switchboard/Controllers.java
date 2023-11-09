package dev.switchboard;

import dev.switchboard.Models.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

public final class Controllers {
    private Controllers() {}

    static String actor(HttpServletRequest req) {
        String a = req.getHeader("X-Actor");
        if (a == null || a.isBlank()) return "admin";
        return a.replaceAll("[^\\p{L}\\p{N} ._@-]", "").trim().substring(0, Math.min(60, a.length()));
    }

    @RestController
    @RequestMapping("/api/v1")
    @Tag(name = "Management", description = "Admin token required (Authorization: Bearer <SWITCHBOARD_ADMIN_TOKEN>)")
    @SecurityRequirement(name = "admin")
    public static class Management {
        private final FlagService flags;
        private final FlagRepository repo;

        public Management(FlagService flags, FlagRepository repo) {
            this.flags = flags;
            this.repo = repo;
        }

        @GetMapping("/flags")
        @Operation(summary = "List flags with their per-environment settings")
        public List<Flag> list() { return flags.list(); }

        @PostMapping("/flags")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create a flag (off in every environment)")
        public Flag create(@Valid @RequestBody CreateFlag body, HttpServletRequest req) { return flags.create(body, actor(req)); }

        @GetMapping("/flags/{key}")
        public Flag get(@PathVariable String key) { return flags.get(key); }

        @PatchMapping("/flags/{key}")
        public Flag update(@PathVariable String key, @Valid @RequestBody UpdateFlag body, HttpServletRequest req) { return flags.update(key, body, actor(req)); }

        @DeleteMapping("/flags/{key}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void delete(@PathVariable String key, HttpServletRequest req) { flags.delete(key, actor(req)); }

        @PutMapping("/flags/{key}/environments/{env}")
        @Operation(summary = "Enable/disable a flag or change its rollout percentage in one environment")
        public Flag setEnvironment(@PathVariable String key, @PathVariable String env, @Valid @RequestBody UpdateEnvironment body, HttpServletRequest req) {
            return flags.setEnvironment(key, env, body, actor(req));
        }

        @GetMapping("/flags/{key}/audit")
        @Operation(summary = "Change history of one flag")
        public List<AuditEntry> flagAudit(@PathVariable String key, @RequestParam(defaultValue = "100") int limit) { return flags.audit(key, limit); }

        @GetMapping("/audit")
        @Operation(summary = "Change history across all flags")
        public List<AuditEntry> audit(@RequestParam(defaultValue = "100") int limit) { return flags.audit(null, limit); }

        @GetMapping("/environments")
        public List<EnvironmentInfo> environments() { return repo.environmentInfo(); }

        @PostMapping("/environments/{env}/rotate-key")
        @Operation(summary = "Issue a new SDK key for an environment. The key is returned once; the old key stops working.")
        public NewKey rotate(@PathVariable String env, HttpServletRequest req) { return flags.rotateKey(env, actor(req)); }
    }

    @RestController
    @RequestMapping("/api/v1/evaluate")
    @Tag(name = "Evaluation", description = "Environment SDK key required (Authorization: Bearer <sdk key>)")
    @SecurityRequirement(name = "sdk")
    public static class Evaluation_ {
        private final FlagService flags;

        public Evaluation_(FlagService flags) { this.flags = flags; }

        @PostMapping
        @Operation(summary = "Evaluate one flag for a user in the environment of the SDK key")
        public Models.Evaluation evaluate(@Valid @RequestBody EvaluateRequest body, Authentication auth) {
            return flags.evaluate(auth.getName(), body.flagKey(), body.userId());
        }

        @GetMapping
        @Operation(summary = "Evaluate every flag for a user")
        public EvaluateAll all(@RequestParam(required = false) String userId, Authentication auth) {
            return new EvaluateAll(auth.getName(), flags.evaluateAll(auth.getName(), userId));
        }
    }

    @RestControllerAdvice
    public static class Errors {
        @ExceptionHandler(FlagService.NotFoundException.class)
        ResponseEntity<ApiError> notFound(FlagService.NotFoundException e) { return build(HttpStatus.NOT_FOUND, e.getMessage()); }

        @ExceptionHandler(FlagService.ConflictException.class)
        ResponseEntity<ApiError> conflict(FlagService.ConflictException e) { return build(HttpStatus.CONFLICT, e.getMessage()); }

        @ExceptionHandler(FlagService.BadRequestException.class)
        ResponseEntity<ApiError> bad(FlagService.BadRequestException e) { return build(HttpStatus.BAD_REQUEST, e.getMessage()); }

        @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
        ResponseEntity<ApiError> invalid(org.springframework.web.bind.MethodArgumentNotValidException e) {
            Map<String, String> fields = new java.util.LinkedHashMap<>();
            e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
            return ResponseEntity.badRequest().body(new ApiError("Validation failed", fields, null));
        }

        @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
        ResponseEntity<ApiError> unreadable(Exception e) { return build(HttpStatus.BAD_REQUEST, "Malformed JSON body"); }

        private static ResponseEntity<ApiError> build(HttpStatus s, String m) { return ResponseEntity.status(s).body(new ApiError(m, null, null)); }
    }
}
