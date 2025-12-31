package com.listaai.infrastructure.adapter.output.persistence.mapper

import com.listaai.domain.model.ShoppingList
import com.listaai.infrastructure.adapter.output.persistence.entity.ListEntity
import org.mapstruct.Mapper

@Mapper(componentModel = "spring")
interface ListPersistenceMapper {

    fun toDomain(list: ListEntity): ShoppingList
    fun toEntity(list: ShoppingList): ListEntity
}