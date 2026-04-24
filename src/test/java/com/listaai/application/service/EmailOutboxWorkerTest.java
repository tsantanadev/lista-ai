package com.listaai.application.service;

import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailOutboxRepository;
import com.listaai.application.port.output.EmailOutboxRepository.OutboxRow;
import com.listaai.application.port.output.EmailSender;
import com.listaai.application.service.exception.EmailSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailOutboxWorkerTest {

    private EmailOutboxRepository outboxRepo;
    private EmailSender sender;
    private EmailTemplateRenderer renderer;
    private Clock clock;
    private EmailOutboxWorker worker;

    private static final Instant NOW = Instant.parse("2026-04-23T10:00:00Z");

    @BeforeEach
    void setUp() {
        outboxRepo = mock(EmailOutboxRepository.class);
        sender = mock(EmailSender.class);
        renderer = mock(EmailTemplateRenderer.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        worker = new EmailOutboxWorker(outboxRepo, sender, renderer, clock);
    }

    @Test
    void success_marks_sent() {
        OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", 0);
        when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
        when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));

        worker.processOutbox();

        verify(sender).send(new EmailMessage("a@b", "s", "h", "t"));
        verify(outboxRepo).markSent(1L, NOW);
    }

    @Test
    void retryable_exception_schedules_retry_with_backoff() {
        OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", 0);
        when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
        when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));
        doThrow(new EmailSendException("boom", true)).when(sender).send(any());

        worker.processOutbox();

        ArgumentCaptor<Instant> nextCap = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepo).markRetry(eq(1L), eq(1), nextCap.capture(), eq("boom"));
        // backoff(1) = 10s
        assertThat(nextCap.getValue()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void non_retryable_exception_marks_failed_immediately() {
        OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", 0);
        when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
        when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));
        doThrow(new EmailSendException("bad addr", false)).when(sender).send(any());

        worker.processOutbox();

        verify(outboxRepo).markFailed(1L, 1, "bad addr");
        verify(outboxRepo, never()).markRetry(anyLong(), anyInt(), any(), any());
    }

    @Test
    void at_max_attempts_marks_failed_even_if_retryable() {
        OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", 7); // MAX=8, attempts+1=8 → fail
        when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
        when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));
        doThrow(new EmailSendException("still down", true)).when(sender).send(any());

        worker.processOutbox();

        verify(outboxRepo).markFailed(1L, 8, "still down");
        verify(outboxRepo, never()).markRetry(anyLong(), anyInt(), any(), any());
    }

    @Test
    void backoff_sequence_matches_spec() {
        // backoff(n): 10s, 30s, 1m, 5m, 15m, 1h, 4h, 12h
        long[] expectedSeconds = {10, 30, 60, 300, 900, 3600, 14400, 43200};
        for (int attempts = 0; attempts < expectedSeconds.length; attempts++) {
            OutboxRow row = new OutboxRow(1L, "VERIFY_EMAIL", "a@b", "{}", attempts);
            when(outboxRepo.claimPending(NOW, 50)).thenReturn(List.of(row));
            when(renderer.render(row)).thenReturn(new EmailMessage("a@b", "s", "h", "t"));
            doThrow(new EmailSendException("boom", true)).when(sender).send(any());

            worker.processOutbox();

            int expectedAttempts = attempts + 1;
            if (expectedAttempts < 8) {
                verify(outboxRepo).markRetry(eq(1L), eq(expectedAttempts),
                        eq(NOW.plusSeconds(expectedSeconds[attempts])), eq("boom"));
            }
            reset(outboxRepo, sender, renderer);
        }
    }
}
