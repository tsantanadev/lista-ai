package com.listaai.infrastructure.adapter.output.persistence.mapper;

import com.listaai.domain.model.ItemList;
import com.listaai.infrastructure.adapter.output.persistence.entity.ItemListEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ItemListPersistenceMapper {
    ItemList toDomain(ItemListEntity entity);

    @Mapping(target = "listId", ignore = true)
    ItemListEntity toEntity(ItemList domain);
}
