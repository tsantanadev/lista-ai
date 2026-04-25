package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.BaseIntegrationTest;
import com.listaai.application.port.output.UserRepository;
import com.listaai.domain.model.EmailVerificationToken;
import com.listaai.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationTokenPersistenceAdapterIT extends BaseIntegrationTest {

    @Autowired EmailVerificationTokenPersistenceAdapter adapter;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void markRevoked_setsRevokedAt() {
        User user = userRepository.save(
                new User(null, "revoke@example.com", "R", false), "HASH");
        Instant createdAt = Instant.parse("2026-04-25T09:00:00Z");
        Instant expiresAt = createdAt.plusSeconds(86_400);
        EmailVerificationToken token = adapter.save(user.id(), "hash-1", expiresAt, createdAt);

        adapter.markRevoked(token.id(), Instant.parse("2026-04-25T10:00:00Z"));

        Integer revokedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE id = ? AND revoked_at IS NOT NULL",
                Integer.class, token.id());
        assertThat(revokedCount).isEqualTo(1);
    }

    @Test
    void markRevoked_noOpWhenIdMissing() {
        adapter.markRevoked(9_999_999L, Instant.now());
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE id = 9999999",
                Integer.class);
        assertThat(rows).isZero();
    }
}
