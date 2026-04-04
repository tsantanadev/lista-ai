package com.listaai.application.port.output;

import com.listaai.domain.model.OAuthIdentity;
import java.util.Optional;

public interface OAuthIdentityRepository {
    Optional<OAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
    OAuthIdentity save(OAuthIdentity identity);
}
