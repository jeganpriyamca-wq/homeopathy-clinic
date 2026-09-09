package com.homeopathy.clinic.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity h) throws Exception {
    JwtGrantedAuthoritiesConverter g = new JwtGrantedAuthoritiesConverter();
    g.setAuthoritiesClaimName("role");
    g.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter j = new JwtAuthenticationConverter();
    j.setJwtGrantedAuthoritiesConverter(g);
    h.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/auth/login", "/api/health")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/doctor/**")
                    .hasAnyRole("DOCTOR", "ADMIN")
                    .requestMatchers("/api/reception/**")
                    .hasAnyRole("RECEPTIONIST", "ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(x -> x.jwtAuthenticationConverter(j)));
    return h.build();
  }
}
