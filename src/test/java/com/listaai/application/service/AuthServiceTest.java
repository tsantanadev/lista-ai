package com.listaai.application.service;

import com.listaai.application.port.input.AuthIdentity;
import com.listaai.application.port.input.AuthProvider;
import com.listaai.application.port.input.AuthProviderRegistry;
import com.listaai.application.port.input.AuthResult;
import com.listaai.application.port.input.command.*;
import com.listaai.application.port.output.OAuthIdentityRepository;
import com.listaai.application.port.output.RefreshTokenRepository;
import com.listaai.application.port.output.UserRepository;
import com.listaai.domain.model.User;
import com.listaai.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OAuthIdentityRepository oAuthIdentityRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuthProviderRegistry authProviderRegistry;
    @Mock private AuthProvider localAuthProvider;
    @Mock private JwtTokenService jwtTokenService;

    private AuthService authService;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // low cost for tests
        authService = new AuthService(
                userRepository, oAuthIdentityRepository, refreshTokenRepository,
                authProviderRegistry, jwtTokenService, passwordEncoder, 7
        );
    }

    @Test
    void register_newEmail_returnsTokens() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(), anyString()))
                .thenReturn(new User(1L, "user@example.com", "Test User"));
        when(jwtTokenService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenService.hashRefreshToken("refresh-token")).thenReturn("hash");

        AuthResult result = authService.register(
                new RegisterCommand("user@example.com", "Password123!", "Test User"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.expiresIn()).isEqualTo(900L);
    }

    @Test
    void register_duplicateEmail_throwsException() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(new User(1L, "user@example.com", "Existing")));

        assertThatThrownBy(() ->
                authService.register(new RegisterCommand("user@example.com", "pass", "name")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void loginLocal_validCredentials_returnsTokens() {
        when(authProviderRegistry.get("local")).thenReturn(localAuthProvider);
        when(localAuthProvider.authenticate(any()))
                .thenReturn(new AuthIdentity("user@example.com", "Test User", null));
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(new User(1L, "user@example.com", "Test User")));
        when(jwtTokenService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenService.hashRefreshToken("refresh-token")).thenReturn("hash");

        AuthResult result = authService.loginLocal(new LoginCommand("user@example.com", "Password123!"));

        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void loginGoogle_newUser_createsUserAndReturnsTokens() {
        AuthProvider googleProvider = mock(AuthProvider.class);
        when(authProviderRegistry.get("google")).thenReturn(googleProvider);
        when(googleProvider.authenticate(any()))
                .thenReturn(new AuthIdentity("user@gmail.com", "Google User", "google-sub-123"));
        when(oAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(), isNull()))
                .thenReturn(new User(2L, "user@gmail.com", "Google User"));
        when(oAuthIdentityRepository.save(any())).thenReturn(null);
        when(jwtTokenService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenService.hashRefreshToken("refresh-token")).thenReturn("hash");

        AuthResult result = authService.loginGoogle(new GoogleAuthCommand("id-token"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(userRepository).save(any(), isNull()); // no password hash for OAuth users
    }

    @Test
    void loginGoogle_existingOAuthIdentity_returnsTokensWithoutCreatingNewUser() {
        AuthProvider googleProvider = mock(AuthProvider.class);
        when(authProviderRegistry.get("google")).thenReturn(googleProvider);
        when(googleProvider.authenticate(any()))
                .thenReturn(new AuthIdentity("user@gmail.com", "Google User", "google-sub-123"));

        com.listaai.domain.model.OAuthIdentity existingIdentity =
                new com.listaai.domain.model.OAuthIdentity(1L, 2L, "google", "google-sub-123");
        when(oAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.of(existingIdentity));
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(new User(2L, "user@gmail.com", "Google User")));
        when(jwtTokenService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtTokenService.hashRefreshToken("refresh-token")).thenReturn("hash");

        AuthResult result = authService.loginGoogle(new GoogleAuthCommand("id-token"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(any(), any()); // no new user created
        verify(oAuthIdentityRepository, never()).save(any()); // no new identity created
    }

    @Test
    void refresh_validToken_returnsNewTokens() {
        when(jwtTokenService.hashRefreshToken("old-token")).thenReturn("old-hash");
        when(refreshTokenRepository.findUserIdByTokenHashIfValid("old-hash"))
                .thenReturn(Optional.of(1L));
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(new User(1L, "user@example.com", "Test User")));
        when(jwtTokenService.generateAccessToken(any())).thenReturn("new-access");
        when(jwtTokenService.generateRefreshToken()).thenReturn("new-refresh");
        when(jwtTokenService.hashRefreshToken("new-refresh")).thenReturn("new-hash");

        AuthResult result = authService.refresh(new RefreshCommand("old-token"));

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenRepository).revoke("old-hash");
    }

    @Test
    void refresh_invalidToken_throwsBadCredentials() {
        when(jwtTokenService.hashRefreshToken("bad-token")).thenReturn("bad-hash");
        when(refreshTokenRepository.findUserIdByTokenHashIfValid("bad-hash"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshCommand("bad-token")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logout_revokesRefreshToken() {
        when(jwtTokenService.hashRefreshToken("token")).thenReturn("hash");
        authService.logout(new RefreshCommand("token"));
        verify(refreshTokenRepository).revoke("hash");
    }
}
