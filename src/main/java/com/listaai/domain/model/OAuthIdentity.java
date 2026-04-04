package com.listaai.domain.model;

public record OAuthIdentity(Long id, Long userId, String provider, String providerUserId) {}
