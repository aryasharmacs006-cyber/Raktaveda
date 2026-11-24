package com.raktaveda.security;

import com.raktaveda.common.ErrorResponseWriter;
import com.raktaveda.config.RaktavedaProperties;
import com.raktaveda.ratelimit.RateLimitFilter;
import com.raktaveda.ratelimit.RateLimiter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final RaktavedaProperties props;
    private final ErrorResponseWriter writer;
    private final TokenService tokens;
    private final RateLimiter limiter;

    public SecurityConfig(RaktavedaProperties props, ErrorResponseWriter writer, TokenService tokens, RateLimiter limiter) {
        this.props = props;
        this.writer = writer;
        this.tokens = tokens;
        this.limiter = limiter;
    }

    /** BCrypt with strength 12 (each hash takes roughly a quarter of a second on purpose). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Swagger pages only. Needs a looser content policy than the API. Off unless RAKTAVEDA_API_DOCS_ENABLED=true. */
    @Bean
    @Order(1)
    public SecurityFilterChain docsChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'")));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
                // CSRF protects cookie sessions. We use a bearer token in a header and no
                // cookies, so there is nothing for a forged page to ride on.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/user/otp/request", "/api/auth/user/otp/verify",
                                "/api/auth/institution/register", "/api/auth/institution/login",
                                "/api/auth/admin/login", "/api/auth/second-step/verify").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // The WebSocket handshake cannot carry our token header. The token is checked on the
                        // STOMP CONNECT frame instead (see StompAuthInterceptor), and origins are checked in WebSocketConfig.
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Later stages add their own rules above this line. Everything else needs a login.
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writer.write(res, 401, "AUTH_REQUIRED", "error.auth.required"))
                        .accessDeniedHandler((req, res, ex) ->
                                writer.write(res, 403, "FORBIDDEN", "error.forbidden")))
                .addFilterBefore(new RateLimitFilter(limiter, props, writer), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new BearerTokenFilter(tokens), UsernamePasswordAuthenticationFilter.class);

        if (props.security().requireHttps()) {
            // Needs review before real use: behind a proxy, also set RAKTAVEDA_FORWARD_HEADERS=native.
            http.requiresChannel(c -> c.anyRequest().requiresSecure());
        }
        return http.build();
    }

    /** Only the origins listed in RAKTAVEDA_ALLOWED_ORIGINS may call the API from a browser. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.security().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Accept-Language"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
