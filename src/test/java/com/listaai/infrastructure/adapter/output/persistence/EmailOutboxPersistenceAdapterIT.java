package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxPersistenceAdapterIT extends BaseIntegrationTest {

    @Autowired EmailOutboxPersistenceAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    @Test
    void markFailed_updatesStatusAttemptsAndLastError() {
        Instant now = Instant.parse("2026-04-25T10:00:00Z");
        Long id = adapter.enqueue("VERIFY_EMAIL", "x@example.com", "{}", now);

        adapter.markFailed(id, 5, "permanent bounce");

        String status = jdbc.queryForObject(
                "SELECT status FROM email_outbox WHERE id = ?", String.class, id);
        int attempts = jdbc.queryForObject(
                "SELECT attempts FROM email_outbox WHERE id = ?", Integer.class, id);
        String lastError = jdbc.queryForObject(
                "SELECT last_error FROM email_outbox WHERE id = ?", String.class, id);

        assertThat(status).isEqualTo("FAILED");
        assertThat(attempts).isEqualTo(5);
        assertThat(lastError).isEqualTo("permanent bounce");
    }

    @Test
    void markFailed_noOpWhenIdMissing() {
        adapter.markFailed(9_999_999L, 1, "ghost");
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_outbox WHERE id = 9999999", Integer.class);
        assertThat(rows).isZero();
    }
}
