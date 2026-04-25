package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.EmailVerificationTokenRepository;
import com.listaai.domain.model.EmailVerificationToken;
import com.listaai.infrastructure.adapter.output.persistence.entity.EmailVerificationTokenEntity;
import com.listaai.infrastructure.adapter.output.persistence.mapper.EmailVerificationTokenPersistenceMapper;
import com.listaai.infrastructure.adapter.output.persistence.repository.EmailVerificationTokenJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

@Component
public class EmailVerificationTokenPersistenceAdapter implements EmailVerificationTokenRepository {

    private final EmailVerificationTokenJpaRepository jpa;
    private final EmailVerificationTokenPersistenceMapper mapper;

    public EmailVerificationTokenPersistenceAdapter(
            EmailVerificationTokenJpaRepository jpa,
            EmailVerificationTokenPersistenceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public EmailVerificationToken save(Long userId, String tokenHash, Instant expiresAt, Instant createdAt) {
        EmailVerificationTokenEntity e = new EmailVerificationTokenEntity(userId, tokenHash, expiresAt, createdAt);
        return mapper.toDomain(jpa.save(e));
    }

    @Override
    public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public Optional<EmailVerificationToken> findLatestByUserId(Long userId) {
        return jpa.findFirstByUserIdOrderByCreatedAtDesc(userId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void markUsed(Long id, Instant usedAt) {
        jpa.findById(id).ifPresent(e -> e.setUsedAt(usedAt));
    }

    @Override
    @Transactional
    public void markRevoked(Long id, Instant revokedAt) {
        jpa.findById(id).ifPresent(e -> e.setRevokedAt(revokedAt));
    }
}
