package com.listaai.infrastructure.adapter.output.persistence.mapper;

import com.listaai.domain.model.ShoppingList;
import com.listaai.infrastructure.adapter.output.persistence.entity.ListEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ListPersistenceMapper {
    ShoppingList toDomain(ListEntity entity);
    ListEntity toEntity(ShoppingList domain);
}
