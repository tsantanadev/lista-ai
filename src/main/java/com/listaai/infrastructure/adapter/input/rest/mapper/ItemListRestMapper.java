package com.listaai.infrastructure.adapter.input.rest.mapper;

import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.domain.model.ItemList;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListPostRequest;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListResponse;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ItemListRestMapper {
    ItemListResponse toResponse(ItemList domain);

    @Mapping(target = "listId", source = "listId")
    CreateItemListCommand toCreateCommand(ItemListPostRequest request, long listId);

    @Mapping(target = "id", source = "itemId")
    @Mapping(target = "listId", source = "listId")
    UpdateItemListCommand toUpdateCommand(ItemListUpdateRequest request, long itemId, long listId);
}
