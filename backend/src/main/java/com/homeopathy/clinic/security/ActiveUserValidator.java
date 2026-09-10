package com.homeopathy.clinic.security;

import com.homeopathy.clinic.user.UserRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ActiveUserValidator implements OAuth2TokenValidator<Jwt> {
    private final UserRepository users;
    public ActiveUserValidator(UserRepository users) { this.users = users; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object userId = token.getClaim("userId");
        if (userId instanceof Number id) {
            boolean allowed = users.findById(id.longValue()).filter(user ->
                user.isActive() && user.getEmail().equalsIgnoreCase(token.getSubject())
                && user.getRole().name().equals(token.getClaimAsString("role"))).isPresent();
            if (allowed) return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Account is inactive or token no longer matches the account.", null));
    }
}
