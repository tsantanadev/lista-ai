package com.listaai.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        String fromAddress,
        String provider,
        Resend resend,
        Worker worker
) {
    public record Resend(String apiKey, String baseUrl) {}
    public record Worker(long pollIntervalMs) {}
}
