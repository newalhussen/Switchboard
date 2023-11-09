package dev.switchboard;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@ConfigurationProperties(prefix = "switchboard")
record SwitchboardProperties(String adminToken, List<String> environments) {}

/**
 * Two kinds of bearer credentials: the admin token manages flags; an environment SDK key may only evaluate flags,
 * and it pins the evaluation to that key's environment.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http, BearerFilter bearer) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.frameOptions(f -> f.deny()))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/evaluate/**").hasRole("SDK")
                .requestMatchers("/api/v1/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> json(res, 401, "Missing or invalid credentials"))
                .accessDeniedHandler((req, res, ex) -> json(res, 403, "This credential cannot access that endpoint")))
            .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static void json(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    @Bean
    FilterRegistrationBean<BearerFilter> bearerRegistration(BearerFilter f) {
        FilterRegistrationBean<BearerFilter> r = new FilterRegistrationBean<>(f);
        r.setEnabled(false); // runs inside the security chain only
        return r;
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info().title("Switchboard API").version("1.0.0").description("Feature flag management and evaluation"))
            .components(new Components()
                .addSecuritySchemes("admin", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer"))
                .addSecuritySchemes("sdk", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")));
    }
}

@org.springframework.stereotype.Component
class BearerFilter extends OncePerRequestFilter {
    private final SwitchboardProperties props;
    private final FlagRepository repo;

    BearerFilter(SwitchboardProperties props, FlagRepository repo) {
        this.props = props;
        this.repo = repo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = h.substring(7).trim();
            if (Keys.constantTimeEquals(token, props.adminToken())) {
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            } else if (token.startsWith("sb_")) {
                Optional<String> env = repo.environmentForKeyHash(Keys.hash(token));
                env.ifPresent(e -> SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(e, null, List.of(new SimpleGrantedAuthority("ROLE_SDK")))));
            }
        }
        chain.doFilter(req, res);
    }
}
