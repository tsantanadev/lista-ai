package com.listaai.infrastructure.adapter.output.persistence.repository;

import com.listaai.infrastructure.adapter.output.persistence.entity.EmailVerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmailVerificationTokenJpaRepository
        extends JpaRepository<EmailVerificationTokenEntity, Long> {
    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);
    Optional<EmailVerificationTokenEntity> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
