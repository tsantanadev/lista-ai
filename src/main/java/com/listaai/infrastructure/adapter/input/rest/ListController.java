package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.application.port.input.ListService;
import com.listaai.application.port.input.command.CreateListCommand;
import com.listaai.domain.model.ShoppingList;
import com.listaai.infrastructure.adapter.input.rest.dto.ListRequest;
import com.listaai.infrastructure.adapter.input.rest.dto.ListResponse;
import com.listaai.infrastructure.adapter.input.rest.mapper.ListRestMapper;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/lists")
@Tag(name = "Shopping Lists", description = "Create, retrieve, and delete shopping lists")
@SecurityRequirement(name = "bearerAuth")
public class ListController {

    private final ListService listService;
    private final ListRestMapper mapper;

    public ListController(ListService listService, ListRestMapper mapper) {
        this.listService = listService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Get all shopping lists",
               description = "Returns all shopping lists owned by the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lists retrieved successfully",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ListResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content)
    })
    public ResponseEntity<List<ListResponse>> getAllLists(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(listService.getAllLists(userId).stream()
                .map(mapper::toResponse).toList());
    }

    @PostMapping
    @Operation(summary = "Create a shopping list",
               description = "Creates a new shopping list for the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "List created successfully",
            content = @Content(schema = @Schema(implementation = ListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content)
    })
    public ResponseEntity<ListResponse> createList(
            @RequestBody ListRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        ShoppingList created = listService.createList(new CreateListCommand(request.name(), userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a shopping list",
               description = "Deletes the shopping list with the given ID. Only the owner can delete their list.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "List deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token",
            content = @Content),
        @ApiResponse(responseCode = "403", description = "List belongs to a different user",
            content = @Content)
    })
    public ResponseEntity<Void> deleteList(
            @Parameter(description = "ID of the shopping list to delete", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        listService.deleteList(id, userId);
        return ResponseEntity.noContent().build();
    }
}
