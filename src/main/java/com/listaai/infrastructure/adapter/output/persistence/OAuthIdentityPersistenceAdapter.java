package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.OAuthIdentityRepository;
import com.listaai.domain.model.OAuthIdentity;
import com.listaai.infrastructure.adapter.output.persistence.entity.OAuthIdentityEntity;
import com.listaai.infrastructure.adapter.output.persistence.mapper.OAuthIdentityPersistenceMapper;
import com.listaai.infrastructure.adapter.output.persistence.repository.OAuthIdentityJpaRepository;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class OAuthIdentityPersistenceAdapter implements OAuthIdentityRepository {

    private final OAuthIdentityJpaRepository jpaRepository;
    private final OAuthIdentityPersistenceMapper mapper;

    public OAuthIdentityPersistenceAdapter(OAuthIdentityJpaRepository jpaRepository,
                                           OAuthIdentityPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<OAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId) {
        return jpaRepository.findByProviderAndProviderUserId(provider, providerUserId).map(mapper::toDomain);
    }

    @Override
    public OAuthIdentity save(OAuthIdentity identity) {
        OAuthIdentityEntity entity = new OAuthIdentityEntity(
                identity.userId(), identity.provider(), identity.providerUserId());
        return mapper.toDomain(jpaRepository.save(entity));
    }
}
