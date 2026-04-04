package com.listaai.infrastructure.security;

import com.listaai.domain.model.User;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final byte[] secretBytes;
    private final long expirationSeconds;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this.secretBytes = Base64.getDecoder().decode(secret);
        this.expirationSeconds = expirationSeconds;
    }

    public String generateAccessToken(User user) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.id().toString())
                    .claim("email", user.email())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(expirationSeconds, ChronoUnit.SECONDS)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secretBytes));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT", e);
        }
    }

    public Long extractUserId(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return Long.parseLong(jwt.getJWTClaimsSet().getSubject());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }

    public boolean isTokenValid(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            boolean signatureValid = jwt.verify(new MACVerifier(secretBytes));
            boolean notExpired = jwt.getJWTClaimsSet().getExpirationTime().after(new Date());
            return signatureValid && notExpired;
        } catch (Exception e) {
            return false;
        }
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
