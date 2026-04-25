package com.listaai.application.port.output;

import com.listaai.domain.model.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository {
    EmailVerificationToken save(Long userId, String tokenHash, Instant expiresAt, Instant createdAt);
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    Optional<EmailVerificationToken> findLatestByUserId(Long userId);
    void markUsed(Long id, Instant usedAt);
    void markRevoked(Long id, Instant revokedAt);
}
