package com.listaai.application.service;

import com.listaai.application.port.input.AuthProvider;
import com.listaai.application.port.input.AuthProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AuthProviderRegistryImpl implements AuthProviderRegistry {

    private final Map<String, AuthProvider> providers;

    public AuthProviderRegistryImpl(List<AuthProvider> authProviders) {
        this.providers = authProviders.stream()
                .collect(Collectors.toMap(AuthProvider::providerName, Function.identity()));
    }

    @Override
    public AuthProvider get(String providerName) {
        AuthProvider provider = providers.get(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("No auth provider registered for: " + providerName);
        }
        return provider;
    }
}
