package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.RefreshTokenRepository;
import com.listaai.infrastructure.adapter.output.persistence.entity.RefreshTokenEntity;
import com.listaai.infrastructure.adapter.output.persistence.repository.RefreshTokenJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    public RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Long userId, String tokenHash, Instant expiresAt) {
        jpaRepository.save(new RefreshTokenEntity(tokenHash, userId, expiresAt));
    }

    @Override
    public Optional<Long> findUserIdByTokenHashIfValid(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash)
                .filter(t -> !t.isRevoked())
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .map(RefreshTokenEntity::getUserId);
    }

    @Override
    @Transactional
    public void revoke(String tokenHash) {
        jpaRepository.findByTokenHash(tokenHash).ifPresent(RefreshTokenEntity::revoke);
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        jpaRepository.revokeAllByUserId(userId);
    }
}
