package com.listaai.infrastructure.security;

import com.listaai.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtTokenServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHktMzItY2hhcnMhISE=";
    private static final long EXPIRATION_SECONDS = 900L;

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, EXPIRATION_SECONDS);
    }

    @Test
    void generateToken_returnsNonNullToken() {
        User user = new User(42L, "user@example.com", "Test User", true);
        String token = jwtTokenService.generateAccessToken(user);
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUserId_returnsCorrectId() {
        User user = new User(42L, "user@example.com", "Test User", true);
        String token = jwtTokenService.generateAccessToken(user);
        Long userId = jwtTokenService.extractUserId(token);
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void isTokenValid_trueForFreshToken() {
        User user = new User(1L, "user@example.com", "Test User", true);
        String token = jwtTokenService.generateAccessToken(user);
        assertThat(jwtTokenService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_falseForTamperedToken() {
        User user = new User(1L, "user@example.com", "Test User", true);
        String token = jwtTokenService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 4) + "xxxx";
        assertThat(jwtTokenService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void generateRefreshToken_returnsUniqueNonNullTokens() {
        String token1 = jwtTokenService.generateRefreshToken();
        String token2 = jwtTokenService.generateRefreshToken();
        assertThat(token1).isNotBlank();
        assertThat(token2).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void hashRefreshToken_returnsDeterministicHash() {
        String hash1 = jwtTokenService.hashRefreshToken("some-token");
        String hash2 = jwtTokenService.hashRefreshToken("some-token");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashRefreshToken_differentTokensDifferentHashes() {
        String hash1 = jwtTokenService.hashRefreshToken("token-a");
        String hash2 = jwtTokenService.hashRefreshToken("token-b");
        assertThat(hash1).isNotEqualTo(hash2);
    }
}
