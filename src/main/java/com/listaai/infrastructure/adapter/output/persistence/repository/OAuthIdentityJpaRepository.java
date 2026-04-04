package com.listaai.infrastructure.adapter.output.persistence.repository;

import com.listaai.infrastructure.adapter.output.persistence.entity.OAuthIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OAuthIdentityJpaRepository extends JpaRepository<OAuthIdentityEntity, Long> {
    Optional<OAuthIdentityEntity> findByProviderAndProviderUserId(String provider, String providerUserId);
}
