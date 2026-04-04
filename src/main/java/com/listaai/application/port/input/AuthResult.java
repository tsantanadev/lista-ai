package com.listaai.application.port.input;

// Returned by AuthUseCase to the REST controller
public record AuthResult(String accessToken, String refreshToken, long expiresIn) {}
