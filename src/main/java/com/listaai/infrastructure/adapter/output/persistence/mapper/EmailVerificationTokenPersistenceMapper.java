package com.listaai.infrastructure.adapter.output.persistence.mapper;

import com.listaai.domain.model.EmailVerificationToken;
import com.listaai.infrastructure.adapter.output.persistence.entity.EmailVerificationTokenEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EmailVerificationTokenPersistenceMapper {
    EmailVerificationToken toDomain(EmailVerificationTokenEntity entity);
}
