package com.listaai.infrastructure.adapter.input.rest.mapper

import com.listaai.application.port.input.command.CreateListCommand
import com.listaai.domain.model.ShoppingList
import com.listaai.infrastructure.adapter.input.rest.dto.ListRequest
import com.listaai.infrastructure.adapter.input.rest.dto.ListResponse
import org.mapstruct.Mapper

@Mapper(componentModel = "spring")
interface ListRestMapper {
    fun toResponse(domain: ShoppingList): ListResponse
    fun toCommand(request: ListRequest): CreateListCommand
}