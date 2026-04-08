package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.application.port.input.ItemListService;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListPostRequest;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListResponse;
import com.listaai.infrastructure.adapter.input.rest.dto.ItemListUpdateRequest;
import com.listaai.infrastructure.adapter.input.rest.mapper.ItemListRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/lists/{listId}/items")
@Tag(name = "Shopping List Items", description = "Add, retrieve, update, and delete items within a shopping list")
@SecurityRequirement(name = "bearerAuth")
public class ItemListController {

    private final ItemListService service;
    private final ItemListRestMapper mapper;

    public ItemListController(ItemListService service, ItemListRestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Get all items in a list",
               description = "Returns all items belonging to the specified shopping list.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Items retrieved successfully",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemListResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content)
    })
    public ResponseEntity<List<ItemListResponse>> getItemsList(
            @Parameter(description = "ID of the shopping list", required = true)
            @PathVariable long listId) {
        var result = service.getItemsList(listId).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Operation(summary = "Add an item to a list",
               description = "Creates a new item in the specified shopping list. The item starts as unchecked.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Item added successfully",
            content = @Content(schema = @Schema(implementation = ItemListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content)
    })
    public ResponseEntity<ItemListResponse> postItemList(
            @RequestBody ItemListPostRequest request,
            @Parameter(description = "ID of the shopping list", required = true)
            @PathVariable long listId) {
        var command = mapper.toCreateCommand(request, listId);
        var created = service.save(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Update an item",
               description = "Updates the description and/or checked status of an existing item.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Item updated successfully",
            content = @Content(schema = @Schema(implementation = ItemListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content)
    })
    public ResponseEntity<ItemListResponse> putItemList(
            @RequestBody ItemListUpdateRequest request,
            @Parameter(description = "ID of the shopping list", required = true)
            @PathVariable long listId,
            @Parameter(description = "ID of the item to update", required = true)
            @PathVariable long itemId) {
        var command = mapper.toUpdateCommand(request, itemId, listId);
        var result = service.update(command);
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an item",
               description = "Removes an item from the shopping list.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Item deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content)
    })
    public ResponseEntity<Void> deleteItemList(
            @Parameter(description = "ID of the item to delete", required = true)
            @PathVariable long id,
            @Parameter(description = "ID of the shopping list", required = true)
            @PathVariable long listId) {
        service.delete(listId, id);
        return ResponseEntity.noContent().build();
    }
}
