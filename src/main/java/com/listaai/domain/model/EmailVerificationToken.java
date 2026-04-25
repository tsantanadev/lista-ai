package com.listaai.domain.model;

import java.time.Instant;

public record EmailVerificationToken(
        Long id,
        Long userId,
        Instant expiresAt,
        Instant usedAt,
        Instant revokedAt,
        Instant createdAt
) {
    public boolean isUsed() { return usedAt != null; }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired(Instant now) { return expiresAt.isBefore(now); }
}
