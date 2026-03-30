package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.application.port.input.ItemListService;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListPostRequest;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListResponse;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListUpdateRequest;
import com.listaai.infrastructure.adapter.input.rest.mapper.ItemListRestMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/lists/{listId}/items")
public class ItemListController {

    private final ItemListService service;
    private final ItemListRestMapper mapper;

    public ItemListController(ItemListService service, ItemListRestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<List<ItemListResponse>> getItemsList(@PathVariable long listId) {
        var result = service.getItemsList(listId).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Void> postItemList(
            @RequestBody ItemListPostRequest request,
            @PathVariable long listId) {
        var command = mapper.toCreateCommand(request, listId);
        service.save(command);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<ItemListResponse> putItemList(
            @RequestBody ItemListUpdateRequest request,
            @PathVariable long listId,
            @PathVariable long itemId) {
        var command = mapper.toUpdateCommand(request, itemId, listId);
        var result = service.update(command);
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItemList(
            @PathVariable long id,
            @PathVariable long listId) {
        service.delete(listId, id);
        return ResponseEntity.noContent().build();
    }
}
