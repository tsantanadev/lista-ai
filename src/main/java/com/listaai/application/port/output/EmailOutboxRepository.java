package com.listaai.application.port.output;

import java.time.Instant;
import java.util.List;

public interface EmailOutboxRepository {

    Long enqueue(String template, String recipient, String payloadJson, Instant now);

    /** Locks and returns rows with status=PENDING and next_attempt_at <= now. */
    List<OutboxRow> claimPending(Instant now, int limit);

    void markSent(Long id, Instant sentAt);
    void markRetry(Long id, int attempts, Instant nextAttemptAt, String lastError);
    void markFailed(Long id, int attempts, String lastError);

    record OutboxRow(
            Long id,
            String template,
            String recipient,
            String payloadJson,
            int attempts
    ) {}
}
