package com.listaai.application.service;

import com.listaai.application.port.input.AuthIdentity;
import com.listaai.application.port.input.AuthProvider;
import com.listaai.application.port.input.AuthProviderRegistry;
import com.listaai.application.port.input.AuthResult;
import com.listaai.application.port.input.command.*;
import com.listaai.application.port.output.EmailOutboxRepository;
import com.listaai.application.port.output.EmailVerificationTokenRepository;
import com.listaai.application.port.output.OAuthIdentityRepository;
import com.listaai.application.port.output.RefreshTokenRepository;
import com.listaai.application.port.output.UserRepository;
import com.listaai.application.service.exception.EmailNotVerifiedException;
import com.listaai.application.service.exception.InvalidVerificationTokenException;
import com.listaai.application.service.exception.VerificationCooldownException;
import com.listaai.application.service.exception.VerificationTokenExpiredException;
import com.listaai.application.service.exception.VerificationTokenSupersededException;
import com.listaai.domain.model.EmailVerificationToken;
import com.listaai.domain.model.User;
import com.listaai.infrastructure.config.EmailVerificationProperties;
import com.listaai.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailVerificationTokenRepository verifyTokenRepository;
    @Mock private EmailOutboxRepository outboxRepo;

    private static final Instant NOW = Instant.parse("2026-04-23T10:00:00Z");

    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, oAuthIdentityRepository, refreshTokenRepository,
                authProviderRegistry, jwtTokenService, passwordEncoder, 7,
                fixedClock, verifyTokenRepository, outboxRepo,
                new EmailVerificationProperties(false, 24, 60, "https://app.test/verify")
        );
    }

    @Test
    void register_newEmail_returnsTokens() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("HASHED");
        when(userRepository.save(any(), anyString()))
                .thenReturn(new User(1L, "user@example.com", "Test User", true));
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
                .thenReturn(Optional.of(new User(1L, "user@example.com", "Existing", true)));

        assertThatThrownBy(() ->
                authService.register(new RegisterCommand("user@example.com", "pass", "name")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void loginLocal_validCredentials_returnsTokens() {
        when(authProviderRegistry.get("local")).thenReturn(localAuthProvider);
        when(localAuthProvider.authenticate(any()))
                .thenReturn(new AuthIdentity("user@example.com", "Test User", null, true));
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(new User(1L, "user@example.com", "Test User", true)));
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
                .thenReturn(new AuthIdentity("user@gmail.com", "Google User", "google-sub-123", true));
        when(oAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(), isNull()))
                .thenReturn(new User(2L, "user@gmail.com", "Google User", true));
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
                .thenReturn(new AuthIdentity("user@gmail.com", "Google User", "google-sub-123", true));

        com.listaai.domain.model.OAuthIdentity existingIdentity =
                new com.listaai.domain.model.OAuthIdentity(1L, 2L, "google", "google-sub-123");
        when(oAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.of(existingIdentity));
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(new User(2L, "user@gmail.com", "Google User", true)));
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
                .thenReturn(Optional.of(new User(1L, "user@example.com", "Test User", true)));
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

    @Test
    void register_whenFlagOff_savesUserAsVerified() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pwd")).thenReturn("HASHED");
        when(userRepository.save(any(User.class), eq("HASHED")))
            .thenAnswer(inv -> {
                User u = inv.getArgument(0);
                return new User(42L, u.email(), u.name(), u.verified());
            });

        authService.register(new RegisterCommand("new@example.com", "pwd", "New User"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture(), eq("HASHED"));
        assertThat(captor.getValue().verified()).isTrue();
    }

    @Test
    void issueTokens_usesInjectedClockForExpiry() {
        Clock fixed = Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);
        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixed, verifyTokenRepository, outboxRepo,
                new EmailVerificationProperties(false, 24, 60, "https://app.test/verify"));

        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("H");
        when(userRepository.save(any(User.class), any()))
            .thenAnswer(inv -> {
                User u = inv.getArgument(0);
                return new User(1L, u.email(), u.name(), u.verified());
            });
        when(jwtTokenService.generateRefreshToken()).thenReturn("RAW");
        when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");

        svc.register(new RegisterCommand("x@x.com", "pwd", "X"));

        verify(refreshTokenRepository).save(eq(1L), eq("HASH"),
            eq(Instant.parse("2026-04-30T10:00:00Z")));
    }

    @Test
    void register_whenFlagOn_createsUnverifiedUser_issuesVerificationToken_enqueuesOutbox_returnsNull() {
        EmailVerificationProperties verifyProps = new EmailVerificationProperties(
                true, 24, 60, "https://app.test/verify");

        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo, verifyProps);

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pw")).thenReturn("H");
        when(userRepository.save(any(User.class), eq("H")))
            .thenAnswer(inv -> {
                User u = inv.getArgument(0);
                return new User(10L, u.email(), u.name(), u.verified());
            });
        when(jwtTokenService.generateRefreshToken()).thenReturn("RAW_VTOKEN");
        when(jwtTokenService.hashRefreshToken("RAW_VTOKEN")).thenReturn("HASHED_VTOKEN");

        AuthResult result = svc.register(new RegisterCommand("a@b.com", "pw", "A"));

        assertThat(result).isNull();

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture(), eq("H"));
        assertThat(userCap.getValue().verified()).isFalse();

        verify(verifyTokenRepository).save(eq(10L), eq("HASHED_VTOKEN"), any(Instant.class), any(Instant.class));
        verify(outboxRepo).enqueue(eq("VERIFY_EMAIL"), eq("a@b.com"), contains("RAW_VTOKEN"), any(Instant.class));
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void register_whenFlagOff_existingFastPathUnchanged() {
        EmailVerificationProperties verifyProps = new EmailVerificationProperties(
                false, 24, 60, "https://app.test/verify");

        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo, verifyProps);

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pw")).thenReturn("H");
        when(userRepository.save(any(User.class), eq("H")))
            .thenAnswer(inv -> {
                User u = inv.getArgument(0);
                return new User(10L, u.email(), u.name(), u.verified());
            });
        when(jwtTokenService.generateAccessToken(any())).thenReturn("ACCESS");
        when(jwtTokenService.generateRefreshToken()).thenReturn("REFRESH");
        when(jwtTokenService.hashRefreshToken("REFRESH")).thenReturn("RH");

        AuthResult result = svc.register(new RegisterCommand("a@b.com", "pw", "A"));

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("ACCESS");
        verify(refreshTokenRepository).save(eq(10L), eq("RH"), any(Instant.class));
        verifyNoInteractions(verifyTokenRepository, outboxRepo);
    }

    @Test
    void verifyEmail_setsUserVerified_andMarksTokenUsed() {
        EmailVerificationToken token = new EmailVerificationToken(
                1L, 42L, NOW.plusSeconds(3600), null, null, NOW);
        when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
        when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.of(token));

        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo,
                new EmailVerificationProperties(false, 24, 60, "https://app.test/verify"));

        svc.verifyEmail(new VerifyEmailCommand("RAW"));

        verify(userRepository).setVerified(42L, true);
        verify(verifyTokenRepository).markUsed(1L, NOW);
    }

    @Test
    void verifyEmail_idempotent_whenAlreadyUsed_noStateChange() {
        EmailVerificationToken token = new EmailVerificationToken(
                1L, 42L, NOW.plusSeconds(3600), NOW.minusSeconds(60), null, NOW.minusSeconds(120));
        when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
        when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.of(token));

        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo,
                new EmailVerificationProperties(false, 24, 60, "https://app.test/verify"));

        svc.verifyEmail(new VerifyEmailCommand("RAW"));

        verify(userRepository, never()).setVerified(anyLong(), anyBoolean());
        verify(verifyTokenRepository, never()).markUsed(anyLong(), any());
    }

    @Test
    void verifyEmail_unknownToken_throwsInvalidToken() {
        when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
        when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.empty());

        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo,
                new EmailVerificationProperties(false, 24, 60, "https://app.test/verify"));

        assertThatThrownBy(() -> svc.verifyEmail(new VerifyEmailCommand("RAW")))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void verifyEmail_expired_throwsExpired() {
        EmailVerificationToken token = new EmailVerificationToken(
                1L, 42L, NOW.minusSeconds(60), null, null, NOW.minusSeconds(3600));
        when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
        when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.of(token));

        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo,
                new EmailVerificationProperties(false, 24, 60, "https://app.test/verify"));

        assertThatThrownBy(() -> svc.verifyEmail(new VerifyEmailCommand("RAW")))
                .isInstanceOf(VerificationTokenExpiredException.class);
    }

    @Test
    void verifyEmail_revoked_throwsSuperseded() {
        EmailVerificationToken token = new EmailVerificationToken(
                1L, 42L, NOW.plusSeconds(3600), null, NOW.minusSeconds(60), NOW.minusSeconds(120));
        when(jwtTokenService.hashRefreshToken("RAW")).thenReturn("HASH");
        when(verifyTokenRepository.findByTokenHash("HASH")).thenReturn(Optional.of(token));

        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo,
                new EmailVerificationProperties(false, 24, 60, "https://app.test/verify"));

        assertThatThrownBy(() -> svc.verifyEmail(new VerifyEmailCommand("RAW")))
                .isInstanceOf(VerificationTokenSupersededException.class);
    }

    @Test
    void resend_forUnknownEmail_returnsSilently() {
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());
        authService.resendVerification(new ResendVerificationCommand("x@x.com"));
        verifyNoInteractions(verifyTokenRepository, outboxRepo);
    }

    @Test
    void resend_forAlreadyVerifiedUser_returnsSilently() {
        when(userRepository.findByEmail("v@x.com"))
            .thenReturn(Optional.of(new User(1L, "v@x.com", "V", true)));
        authService.resendVerification(new ResendVerificationCommand("v@x.com"));
        verifyNoInteractions(verifyTokenRepository, outboxRepo);
    }

    @Test
    void resend_withinCooldown_throws429() {
        when(userRepository.findByEmail("u@x.com"))
            .thenReturn(Optional.of(new User(1L, "u@x.com", "U", false)));
        EmailVerificationToken recent = new EmailVerificationToken(
                9L, 1L, NOW.plusSeconds(3600), null, null, NOW.minusSeconds(30));
        when(verifyTokenRepository.findLatestByUserId(1L)).thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> authService.resendVerification(new ResendVerificationCommand("u@x.com")))
                .isInstanceOf(VerificationCooldownException.class);
        verifyNoInteractions(outboxRepo);
    }

    @Test
    void resend_afterCooldown_revokesOldAndIssuesNew() {
        when(userRepository.findByEmail("u@x.com"))
            .thenReturn(Optional.of(new User(1L, "u@x.com", "U", false)));
        EmailVerificationToken old = new EmailVerificationToken(
                9L, 1L, NOW.plusSeconds(3600), null, null, NOW.minusSeconds(120));
        when(verifyTokenRepository.findLatestByUserId(1L)).thenReturn(Optional.of(old));
        when(jwtTokenService.generateRefreshToken()).thenReturn("NEW_RAW");
        when(jwtTokenService.hashRefreshToken("NEW_RAW")).thenReturn("NEW_HASH");

        authService.resendVerification(new ResendVerificationCommand("u@x.com"));

        verify(verifyTokenRepository).markRevoked(9L, NOW);
        verify(verifyTokenRepository).save(eq(1L), eq("NEW_HASH"), any(Instant.class), any(Instant.class));
        verify(outboxRepo).enqueue(eq("VERIFY_EMAIL"), eq("u@x.com"), contains("NEW_RAW"), any(Instant.class));
    }

    @Test
    void resend_firstTime_noPriorToken_issuesNew() {
        when(userRepository.findByEmail("u@x.com"))
            .thenReturn(Optional.of(new User(1L, "u@x.com", "U", false)));
        when(verifyTokenRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());
        when(jwtTokenService.generateRefreshToken()).thenReturn("NEW_RAW");
        when(jwtTokenService.hashRefreshToken("NEW_RAW")).thenReturn("NEW_HASH");

        authService.resendVerification(new ResendVerificationCommand("u@x.com"));

        verify(verifyTokenRepository, never()).markRevoked(anyLong(), any());
        verify(verifyTokenRepository).save(eq(1L), eq("NEW_HASH"), any(Instant.class), any(Instant.class));
        verify(outboxRepo).enqueue(eq("VERIFY_EMAIL"), eq("u@x.com"), contains("NEW_RAW"), any(Instant.class));
    }

    @Test
    void loginLocal_whenFlagOn_andUnverifiedUser_throwsEmailNotVerified() {
        EmailVerificationProperties verifyProps = new EmailVerificationProperties(
                true, 24, 60, "https://app.test/verify");
        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo, verifyProps);

        User unverified = new User(1L, "u@x.com", "U", false);
        AuthProvider localProvider = mock(AuthProvider.class);
        when(authProviderRegistry.get("local")).thenReturn(localProvider);
        when(localProvider.authenticate(any())).thenReturn(new AuthIdentity("u@x.com", "U", null, false));
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> svc.loginLocal(new LoginCommand("u@x.com", "pw")))
                .isInstanceOf(EmailNotVerifiedException.class);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void loginLocal_whenFlagOff_andUnverifiedUser_succeeds() {
        EmailVerificationProperties verifyProps = new EmailVerificationProperties(
                false, 24, 60, "https://app.test/verify");
        AuthService svc = new AuthService(userRepository, oAuthIdentityRepository,
                refreshTokenRepository, authProviderRegistry, jwtTokenService,
                passwordEncoder, 7L, fixedClock, verifyTokenRepository, outboxRepo, verifyProps);

        User unverified = new User(1L, "u@x.com", "U", false);
        AuthProvider localProvider = mock(AuthProvider.class);
        when(authProviderRegistry.get("local")).thenReturn(localProvider);
        when(localProvider.authenticate(any())).thenReturn(new AuthIdentity("u@x.com", "U", null, false));
        when(userRepository.findByEmail("u@x.com")).thenReturn(Optional.of(unverified));
        when(jwtTokenService.generateAccessToken(any())).thenReturn("ACCESS");
        when(jwtTokenService.generateRefreshToken()).thenReturn("RT");
        when(jwtTokenService.hashRefreshToken("RT")).thenReturn("RTH");

        AuthResult result = svc.loginLocal(new LoginCommand("u@x.com", "pw"));
        assertThat(result).isNotNull();
    }
}
