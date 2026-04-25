package com.listaai.application.service;

import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailOutboxRepository;
import com.listaai.application.port.output.EmailOutboxRepository.OutboxRow;
import com.listaai.application.port.output.EmailSender;
import com.listaai.application.service.exception.EmailSendException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.email-verification.enabled", havingValue = "true")
public class EmailOutboxWorker {

    private static final int BATCH_SIZE = 50;
    static final int MAX_ATTEMPTS = 8;

    // backoff for attempt 1..8: 10s, 30s, 1m, 5m, 15m, 1h, 4h, 12h
    private static final Duration[] BACKOFFS = {
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            Duration.ofHours(4),
            Duration.ofHours(12)
    };

    private final EmailOutboxRepository outboxRepo;
    private final EmailSender sender;
    private final EmailTemplateRenderer renderer;
    private final Clock clock;

    public EmailOutboxWorker(EmailOutboxRepository outboxRepo,
                             EmailSender sender,
                             EmailTemplateRenderer renderer,
                             Clock clock) {
        this.outboxRepo = outboxRepo;
        this.sender = sender;
        this.renderer = renderer;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.email.worker.poll-interval-ms}")
    public void processOutbox() {
        Instant now = clock.instant();
        List<OutboxRow> batch = outboxRepo.claimPending(now, BATCH_SIZE);
        for (OutboxRow row : batch) {
            handleRow(row, now);
        }
    }

    private void handleRow(OutboxRow row, Instant now) {
        int nextAttempt = row.attempts() + 1;
        try {
            EmailMessage msg = renderer.render(row);
            sender.send(msg);
            outboxRepo.markSent(row.id(), now);
        } catch (EmailSendException e) {
            if (!e.isRetryable() || nextAttempt >= MAX_ATTEMPTS) {
                outboxRepo.markFailed(row.id(), nextAttempt, e.getMessage());
            } else {
                Instant next = now.plus(BACKOFFS[nextAttempt - 1]);
                outboxRepo.markRetry(row.id(), nextAttempt, next, e.getMessage());
            }
        }
    }
}
