package com.listaai.application.service;

import com.listaai.application.port.input.AuthIdentity;
import com.listaai.application.port.input.AuthProvider;
import com.listaai.application.port.input.command.GoogleAuthCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleAuthProvider implements AuthProvider {

    private final NimbusJwtDecoder googleJwtDecoder;
    private final String clientId;

    public GoogleAuthProvider(
            @Value("${app.auth.google.jwks-uri}") String jwksUri,
            @Value("${app.auth.google.client-id}") String clientId) {
        this.clientId = clientId;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            List<String> audience = jwt.getAudience();
            if (audience != null && audience.contains(clientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid audience", null));
        };
        OAuth2TokenValidator<Jwt> withIssuer =
                JwtValidators.createDefaultWithIssuer("https://accounts.google.com");
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator));
        this.googleJwtDecoder = decoder;
    }

    @Override
    public String providerName() {
        return "google";
    }

    @Override
    public AuthIdentity authenticate(Object command) {
        if (!(command instanceof GoogleAuthCommand cmd)) {
            throw new IllegalArgumentException("Expected GoogleAuthCommand");
        }
        Jwt jwt;
        try {
            jwt = googleJwtDecoder.decode(cmd.idToken());
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid Google ID token: " + e.getMessage(), e);
        }
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        return new AuthIdentity(
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                jwt.getSubject(),
                Boolean.TRUE.equals(emailVerified));
    }
}
