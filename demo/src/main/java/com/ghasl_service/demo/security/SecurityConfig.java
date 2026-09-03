package com.ghasl_service.demo.security;

import com.ghasl_service.demo.model.User;
import com.ghasl_service.demo.repository.UserRepository;
import com.ghasl_service.demo.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Collections;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }


    @Bean
    public org.springframework.security.authentication.AuthenticationManager authenticationManager(
            org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Authentication endpoints (public)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        
                        // Lead capture endpoint (public)
                        .requestMatchers("/api/v1/leads").permitAll()
                        
                        // Service endpoints (public - required for homepage service display)
                        .requestMatchers("/api/v1/services/**").permitAll()
                        
                        // Order submission endpoint (public - required for customer orders)
                        .requestMatchers("/api/v1/orders").permitAll()
                        
                        // Static resources and public pages
                        .requestMatchers("/", "/index.html", "/settings.html").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/assets/**").permitAll()
                        
                        // Admin-only pages and endpoints
                        .requestMatchers("/pos.html", "/dashboard.html").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/pos", "/reports").hasRole("ADMIN")
                        
                        // Order management endpoints (admin-only - accept/reject/price)
                        .requestMatchers("/api/v1/orders/**").hasRole("ADMIN")
                        
                        // Any other request requires authentication
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
