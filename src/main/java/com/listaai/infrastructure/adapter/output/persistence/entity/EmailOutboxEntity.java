package com.listaai.infrastructure.adapter.output.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_outbox")
public class EmailOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String template;

    @Column(nullable = false)
    private String recipient;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailOutboxEntity() {}

    public EmailOutboxEntity(String template, String recipient, String payloadJson,
                             String status, Instant nextAttemptAt, Instant createdAt) {
        this.template = template;
        this.recipient = recipient;
        this.payloadJson = payloadJson;
        this.status = status;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getTemplate() { return template; }
    public String getRecipient() { return recipient; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getCreatedAt() { return createdAt; }
}
