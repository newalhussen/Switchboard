package dev.switchboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Validates configuration and creates the configured environments (with fresh SDK keys, logged once) on first start. */
@Component
class Bootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);
    private final SwitchboardProperties props;
    private final FlagRepository repo;

    Bootstrap(SwitchboardProperties props, FlagRepository repo) {
        this.props = props;
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.adminToken() == null || props.adminToken().length() < 16) {
            throw new IllegalStateException("SWITCHBOARD_ADMIN_TOKEN must be set to at least 16 characters");
        }
        for (String env : props.environments()) {
            if (!env.matches("[a-z][a-z0-9-]{1,20}")) throw new IllegalStateException("Invalid environment name: " + env);
            if (repo.hasEnvironment(env)) continue;
            String key = Keys.generate(env);
            repo.createEnvironment(env, Keys.hash(key), Keys.prefix(key));
            log.warn("Created environment '{}'. SDK key (shown only once): {}", env, key);
        }
    }
}
