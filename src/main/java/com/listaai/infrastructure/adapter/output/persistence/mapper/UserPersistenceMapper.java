package com.listaai.infrastructure.adapter.output.persistence.mapper;

import com.listaai.domain.model.User;
import com.listaai.infrastructure.adapter.output.persistence.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserPersistenceMapper {
    User toDomain(UserEntity entity);
}
