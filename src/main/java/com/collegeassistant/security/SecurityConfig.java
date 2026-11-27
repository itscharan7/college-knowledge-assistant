package com.collegeassistant.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationSuccessHandler loginSuccessHandler() {
        return (request, response, authentication) -> {
            boolean admin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            response.sendRedirect(request.getContextPath() + (admin ? "/admin" : "/chat"));
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/css/**", "/js/**", "/error",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/admin/**", "/api/admin/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                // JSON API callers get a plain 401 instead of a redirect to the HTML login page.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        request -> request.getRequestURI().startsWith("/api/")));
        // CSRF stays ON (default). The JS client sends the token from <meta> tags in a header.
        return http.build();
    }
}
