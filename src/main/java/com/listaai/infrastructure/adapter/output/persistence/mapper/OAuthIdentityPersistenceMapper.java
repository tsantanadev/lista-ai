package com.listaai.infrastructure.adapter.output.persistence.mapper;

import com.listaai.domain.model.OAuthIdentity;
import com.listaai.infrastructure.adapter.output.persistence.entity.OAuthIdentityEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OAuthIdentityPersistenceMapper {
    OAuthIdentity toDomain(OAuthIdentityEntity entity);
}
