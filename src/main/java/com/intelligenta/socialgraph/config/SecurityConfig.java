package com.intelligenta.socialgraph.config;

import com.intelligenta.socialgraph.security.TokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Spring Security configuration for token-based authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final AppProperties appProperties;

    public SecurityConfig(TokenAuthenticationFilter tokenAuthenticationFilter, 
                          AppProperties appProperties) {
        this.tokenAuthenticationFilter = tokenAuthenticationFilter;
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/login",
                    "/api/register",
                    "/api/ping",
                    "/api/session",
                    "/api/activate"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
