package org.logetd.config;

import tools.jackson.databind.ObjectMapper;
import org.logetd.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Coût 12 imposé par le cahier des charges.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Handshake HTTP du WebSocket : le JWT est vérifié dans le handler.
                        .requestMatchers("/ws/messages").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/listings").hasRole("OWNER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/listings/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/listings/*/photos").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/listings/*/places").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/visits").hasRole("TENANT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/visits/*/confirm").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/visits/*/proof").hasRole("TENANT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings").hasRole("TENANT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/accept").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/deposit-pending").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/confirm").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasRole("TENANT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/*/confirm").hasRole("OWNER")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                GlobalExceptionHandler.writeSecurityError(
                                        response, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHENTICATED", "Authentification requise."))
                        .accessDeniedHandler((request, response, ex) ->
                                GlobalExceptionHandler.writeSecurityError(
                                        response, objectMapper, HttpStatus.FORBIDDEN,
                                        "ACCESS_DENIED", "Accès refusé.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
