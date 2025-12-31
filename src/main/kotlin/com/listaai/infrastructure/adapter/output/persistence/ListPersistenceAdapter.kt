package com.listaai.infrastructure.adapter.output.persistence

import com.listaai.application.port.output.ListRepository
import com.listaai.domain.model.ShoppingList
import com.listaai.infrastructure.adapter.output.persistence.mapper.ListPersistenceMapper
import com.listaai.infrastructure.adapter.output.persistence.repository.ListJpaRepository
import org.springframework.stereotype.Component

@Component
class ListPersistenceAdapter(
    private val repository: ListJpaRepository,
    private val mapper: ListPersistenceMapper
): ListRepository {

    override fun findAll(): List<ShoppingList> {
        return repository.findAll().map { mapper.toDomain(it) }
    }

    override fun save(list: ShoppingList): ShoppingList {
        val entity = mapper.toEntity(list)
        val persistedEntity = repository.save(entity)
        return mapper.toDomain(persistedEntity)
    }

    override fun delete(id: Long) {
        repository.deleteById(id)
    }
}