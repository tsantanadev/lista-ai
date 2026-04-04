package com.listaai.application.port.input;

// Returned by AuthProvider after validating external credentials
public record AuthIdentity(String email, String name, String providerUserId) {}
