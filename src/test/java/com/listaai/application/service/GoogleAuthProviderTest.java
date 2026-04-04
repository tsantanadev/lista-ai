package com.listaai.application.service;

import com.listaai.application.port.input.AuthIdentity;
import com.listaai.application.port.input.command.GoogleAuthCommand;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.jwk.JWKSet;

class GoogleAuthProviderTest {

    static WireMockServer wireMock;
    static RSAKey testRsaKey;

    @BeforeAll
    static void setup() throws Exception {
        testRsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        wireMock = new WireMockServer(wireMockConfig().port(9191));
        wireMock.start();
        wireMock.stubFor(get("/oauth2/v3/certs")
                .willReturn(okJson(new JWKSet(testRsaKey.toPublicJWK()).toString())));
    }

    @AfterAll
    static void teardown() {
        wireMock.stop();
    }

    private GoogleAuthProvider googleAuthProvider;

    @BeforeEach
    void setUp() {
        googleAuthProvider = new GoogleAuthProvider(
                "http://localhost:9191/oauth2/v3/certs",
                "test-google-client-id"
        );
    }

    @Test
    void providerName_returnsGoogle() {
        assertThat(googleAuthProvider.providerName()).isEqualTo("google");
    }

    @Test
    void authenticate_validToken_returnsAuthIdentity() throws Exception {
        String idToken = buildIdToken("google-sub-123", "user@gmail.com", "Google User",
                "test-google-client-id", Instant.now().plus(1, ChronoUnit.HOURS));

        AuthIdentity identity = googleAuthProvider.authenticate(new GoogleAuthCommand(idToken));

        assertThat(identity.email()).isEqualTo("user@gmail.com");
        assertThat(identity.name()).isEqualTo("Google User");
        assertThat(identity.providerUserId()).isEqualTo("google-sub-123");
    }

    @Test
    void authenticate_wrongAudience_throwsBadCredentials() throws Exception {
        String idToken = buildIdToken("sub", "user@gmail.com", "User",
                "wrong-client-id", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> googleAuthProvider.authenticate(new GoogleAuthCommand(idToken)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticate_expiredToken_throwsBadCredentials() throws Exception {
        String idToken = buildIdToken("sub", "user@gmail.com", "User",
                "test-google-client-id", Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> googleAuthProvider.authenticate(new GoogleAuthCommand(idToken)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticate_invalidToken_throwsBadCredentials() {
        assertThatThrownBy(() -> googleAuthProvider.authenticate(new GoogleAuthCommand("not.a.jwt")))
                .isInstanceOf(BadCredentialsException.class);
    }

    private String buildIdToken(String sub, String email, String name, String audience, Instant expiry)
            throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .claim("email", email)
                .claim("name", name)
                .audience(List.of(audience))
                .issuer("https://accounts.google.com")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiry))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(testRsaKey));
        return jwt.serialize();
    }
}
