package com.listaai.infrastructure.adapter.input.rest

import com.listaai.application.port.input.ListService
import com.listaai.infrastructure.adapter.input.rest.dto.ListRequest
import com.listaai.infrastructure.adapter.input.rest.dto.ListResponse
import com.listaai.infrastructure.adapter.input.rest.mapper.ListRestMapper
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = ["/v1/lists"])
class ListController(
    private val service: ListService,
    private val mapper: ListRestMapper
) {

    @PostMapping
    fun postList(@RequestBody listRequest: ListRequest): ResponseEntity<ListResponse> {
        val command = mapper.toCommand(listRequest)
        val result = service.save(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(result))
    }

    @GetMapping
    fun getList(): ResponseEntity<List<ListResponse>> {
        val result = service.getAll().map { mapper.toResponse(it) }
        return ResponseEntity.ok(result)
    }

    @DeleteMapping(value = ["/{id}"])
    fun deleteList(@PathVariable id: Long): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }
}