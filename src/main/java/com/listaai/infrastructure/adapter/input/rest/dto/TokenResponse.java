package com.listaai.infrastructure.adapter.input.rest.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
