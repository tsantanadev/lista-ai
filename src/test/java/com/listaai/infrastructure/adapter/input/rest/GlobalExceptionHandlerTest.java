package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.application.service.exception.EmailNotVerifiedException;
import com.listaai.application.service.exception.InvalidVerificationTokenException;
import com.listaai.application.service.exception.VerificationCooldownException;
import com.listaai.application.service.exception.VerificationTokenExpiredException;
import com.listaai.application.service.exception.VerificationTokenSupersededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badCredentials_mapsTo401() {
        ProblemDetail pd = handler.handleBadCredentials(new BadCredentialsException("bad"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void illegalState_mapsTo409() {
        ProblemDetail pd = handler.handleConflict(new IllegalStateException("conflict"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void accessDenied_mapsTo403() {
        ProblemDetail pd = handler.handleAccessDenied(new AccessDeniedException("denied"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void invalidVerificationToken_mapsTo400() {
        ProblemDetail pd = handler.handleInvalidVerificationToken(new InvalidVerificationTokenException());
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void verificationTokenExpired_mapsTo410() {
        ProblemDetail pd = handler.handleVerificationTokenExpired(new VerificationTokenExpiredException());
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.GONE.value());
    }

    @Test
    void verificationTokenSuperseded_mapsTo410() {
        ProblemDetail pd = handler.handleVerificationTokenSuperseded(new VerificationTokenSupersededException());
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.GONE.value());
    }

    @Test
    void verificationCooldown_mapsTo429() {
        ProblemDetail pd = handler.handleCooldown(new VerificationCooldownException());
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void emailNotVerified_mapsTo403() {
        ProblemDetail pd = handler.handleEmailNotVerified(new EmailNotVerifiedException());
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
