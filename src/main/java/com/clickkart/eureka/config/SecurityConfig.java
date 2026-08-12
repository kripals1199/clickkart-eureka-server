// src/main/java/com/clickkart/eureka/config/SecurityConfig.java
package com.clickkart.eureka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Eureka's dashboard and registration REST API (/eureka/**) are protected with HTTP Basic
 * auth so the registry cannot be scraped or polluted by unauthenticated clients.
 * CSRF is disabled because Eureka clients register/renew/deregister via stateless REST
 * calls with no CSRF token support - this matches Spring Cloud Netflix's own documented
 * approach for securing a Eureka server.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
