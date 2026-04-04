package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.application.port.input.ListService;
import com.listaai.application.port.input.command.CreateListCommand;
import com.listaai.domain.model.ShoppingList;
import com.listaai.infrastructure.adapter.input.rest.dto.ListRequest;
import com.listaai.infrastructure.adapter.input.rest.dto.ListResponse;
import com.listaai.infrastructure.adapter.input.rest.mapper.ListRestMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/lists")
public class ListController {

    private final ListService listService;
    private final ListRestMapper mapper;

    public ListController(ListService listService, ListRestMapper mapper) {
        this.listService = listService;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<List<ListResponse>> getAllLists(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(listService.getAllLists(userId).stream()
                .map(mapper::toResponse).toList());
    }

    @PostMapping
    public ResponseEntity<ListResponse> createList(
            @RequestBody ListRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        ShoppingList created = listService.createList(new CreateListCommand(request.name(), userId));
        return ResponseEntity.ok(mapper.toResponse(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteList(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        listService.deleteList(id, userId);
        return ResponseEntity.noContent().build();
    }
}
