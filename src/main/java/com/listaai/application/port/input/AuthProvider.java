package com.listaai.application.port.input;

/**
 * Validates provider-specific credentials and returns the authenticated identity.
 * Implement this interface to add a new auth provider (e.g. GitHub, Apple).
 */
public interface AuthProvider {
    String providerName();
    AuthIdentity authenticate(Object command);
}
