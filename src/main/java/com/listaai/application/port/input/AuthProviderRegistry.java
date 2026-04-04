package com.listaai.application.port.input;

public interface AuthProviderRegistry {
    AuthProvider get(String providerName);
}
