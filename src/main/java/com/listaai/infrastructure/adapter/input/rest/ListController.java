package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.application.port.input.ListService;
import com.listaai.infrastructure.adapter.input.rest.dto.ListRequest;
import com.listaai.infrastructure.adapter.input.rest.dto.ListResponse;
import com.listaai.infrastructure.adapter.input.rest.mapper.ListRestMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/lists")
public class ListController {

    private final ListService service;
    private final ListRestMapper mapper;

    public ListController(ListService service, ListRestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ListResponse> postList(@RequestBody ListRequest listRequest) {
        var command = mapper.toCommand(listRequest);
        var result = service.save(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(result));
    }

    @GetMapping
    public ResponseEntity<List<ListResponse>> getList() {
        var result = service.getAll().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteList(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
