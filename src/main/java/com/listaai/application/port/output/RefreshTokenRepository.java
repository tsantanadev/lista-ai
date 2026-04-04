package com.listaai.application.port.output;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {
    void save(Long userId, String tokenHash, Instant expiresAt);
    Optional<Long> findUserIdByTokenHashIfValid(String tokenHash); // returns userId if not revoked and not expired
    void revoke(String tokenHash);
    void revokeAllForUser(Long userId);
}
