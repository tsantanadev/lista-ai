package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.EmailOutboxRepository;
import com.listaai.infrastructure.adapter.output.persistence.entity.EmailOutboxEntity;
import com.listaai.infrastructure.adapter.output.persistence.repository.EmailOutboxJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class EmailOutboxPersistenceAdapter implements EmailOutboxRepository {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";

    private final EmailOutboxJpaRepository jpa;

    public EmailOutboxPersistenceAdapter(EmailOutboxJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Long enqueue(String template, String recipient, String payloadJson, Instant now) {
        EmailOutboxEntity e = new EmailOutboxEntity(template, recipient, payloadJson, STATUS_PENDING, now, now);
        return jpa.save(e).getId();
    }

    @Override
    @Transactional
    public List<OutboxRow> claimPending(Instant now, int limit) {
        return jpa.claimPending(now, limit).stream()
                .map(e -> new OutboxRow(e.getId(), e.getTemplate(), e.getRecipient(), e.getPayloadJson(), e.getAttempts()))
                .toList();
    }

    @Override
    @Transactional
    public void markSent(Long id, Instant sentAt) {
        jpa.findById(id).ifPresent(e -> {
            e.setStatus(STATUS_SENT);
            e.setSentAt(sentAt);
        });
    }

    @Override
    @Transactional
    public void markRetry(Long id, int attempts, Instant nextAttemptAt, String lastError) {
        jpa.findById(id).ifPresent(e -> {
            e.setAttempts(attempts);
            e.setNextAttemptAt(nextAttemptAt);
            e.setLastError(lastError);
        });
    }

    @Override
    @Transactional
    public void markFailed(Long id, int attempts, String lastError) {
        jpa.findById(id).ifPresent(e -> {
            e.setStatus(STATUS_FAILED);
            e.setAttempts(attempts);
            e.setLastError(lastError);
        });
    }
}
