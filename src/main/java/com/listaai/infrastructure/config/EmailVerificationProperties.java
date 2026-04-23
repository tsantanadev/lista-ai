package com.listaai.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(
        boolean enabled,
        int tokenTtlHours,
        int resendCooldownSeconds,
        String redirectBaseUrl
) {}
