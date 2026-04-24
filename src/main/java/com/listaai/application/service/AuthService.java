package com.listaai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.listaai.application.port.input.*;
import com.listaai.application.port.input.command.*;
import com.listaai.application.port.output.*;
import com.listaai.domain.model.OAuthIdentity;
import com.listaai.domain.model.User;
import com.listaai.infrastructure.config.EmailVerificationProperties;
import com.listaai.infrastructure.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService implements AuthUseCase {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository oAuthIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProviderRegistry authProviderRegistry;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final long refreshExpirationDays;
    private final Clock clock;
    private final EmailVerificationTokenRepository verifyTokenRepository;
    private final EmailOutboxRepository outboxRepository;
    private final EmailVerificationProperties verificationProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long ACCESS_TOKEN_EXPIRY_SECONDS = 900L;

    public AuthService(
            UserRepository userRepository,
            OAuthIdentityRepository oAuthIdentityRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuthProviderRegistry authProviderRegistry,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            @Value("${app.jwt.refresh-expiration-days}") long refreshExpirationDays,
            Clock clock,
            EmailVerificationTokenRepository verifyTokenRepository,
            EmailOutboxRepository outboxRepository,
            EmailVerificationProperties verificationProperties) {
        this.userRepository = userRepository;
        this.oAuthIdentityRepository = oAuthIdentityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authProviderRegistry = authProviderRegistry;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.refreshExpirationDays = refreshExpirationDays;
        this.clock = clock;
        this.verifyTokenRepository = verifyTokenRepository;
        this.outboxRepository = outboxRepository;
        this.verificationProperties = verificationProperties;
    }

    @Override
    @Transactional
    public AuthResult register(RegisterCommand command) {
        if (userRepository.findByEmail(command.email()).isPresent()) {
            throw new IllegalStateException("Email already registered: " + command.email());
        }
        String hash = passwordEncoder.encode(command.password());
        boolean verificationEnabled = verificationProperties.enabled();
        User user = userRepository.save(
                new User(null, command.email(), command.name(), !verificationEnabled), hash);

        if (!verificationEnabled) {
            return issueTokens(user);
        }
        issueVerificationEmail(user);
        return null;
    }

    private void issueVerificationEmail(User user) {
        Instant now = clock.instant();
        String rawToken = jwtTokenService.generateRefreshToken();
        String hash = jwtTokenService.hashRefreshToken(rawToken);
        Instant expiresAt = now.plus(verificationProperties.tokenTtlHours(), ChronoUnit.HOURS);
        verifyTokenRepository.save(user.id(), hash, expiresAt, now);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "token", rawToken,
                    "name", user.name()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize verify payload", e);
        }
        outboxRepository.enqueue("VERIFY_EMAIL", user.email(), payload, now);
    }

    @Override
    @Transactional
    public AuthResult loginLocal(LoginCommand command) {
        AuthProvider provider = authProviderRegistry.get("local");
        AuthIdentity identity = provider.authenticate(command);
        User user = userRepository.findByEmail(identity.email())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResult loginGoogle(GoogleAuthCommand command) {
        AuthProvider provider = authProviderRegistry.get("google");
        AuthIdentity identity = provider.authenticate(command);

        Optional<OAuthIdentity> existingIdentity =
                oAuthIdentityRepository.findByProviderAndProviderUserId("google", identity.providerUserId());

        User user;
        if (existingIdentity.isPresent()) {
            user = userRepository.findById(existingIdentity.get().userId())
                    .orElseThrow(() -> new IllegalStateException("User not found for OAuth identity"));
        } else {
            user = userRepository.findByEmail(identity.email())
                    .orElseGet(() -> userRepository.save(
                            new User(null, identity.email(), identity.name(), true), null));
            oAuthIdentityRepository.save(
                    new OAuthIdentity(null, user.id(), "google", identity.providerUserId()));
        }
        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResult refresh(RefreshCommand command) {
        String tokenHash = jwtTokenService.hashRefreshToken(command.refreshToken());
        Long userId = refreshTokenRepository.findUserIdByTokenHashIfValid(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        refreshTokenRepository.revoke(tokenHash);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(RefreshCommand command) {
        String tokenHash = jwtTokenService.hashRefreshToken(command.refreshToken());
        refreshTokenRepository.revoke(tokenHash);
    }

    @Override
    public void verifyEmail(VerifyEmailCommand command) {
        throw new UnsupportedOperationException("Task 13");
    }

    @Override
    public void resendVerification(ResendVerificationCommand command) {
        throw new UnsupportedOperationException("Task 14");
    }

    private AuthResult issueTokens(User user) {
        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshToken = jwtTokenService.generateRefreshToken();
        String refreshTokenHash = jwtTokenService.hashRefreshToken(refreshToken);
        Instant expiresAt = clock.instant().plus(refreshExpirationDays, ChronoUnit.DAYS);
        refreshTokenRepository.save(user.id(), refreshTokenHash, expiresAt);
        return new AuthResult(accessToken, refreshToken, ACCESS_TOKEN_EXPIRY_SECONDS);
    }
}
