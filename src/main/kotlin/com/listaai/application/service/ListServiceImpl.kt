package com.listaai.application.service

import com.listaai.application.port.input.ListService
import com.listaai.application.port.input.command.CreateListCommand
import com.listaai.application.port.output.ListRepository
import com.listaai.domain.model.ShoppingList
import org.springframework.stereotype.Service

@Service
class ListServiceImpl(
    private val repository: ListRepository
) : ListService {

    override fun getAll(): List<ShoppingList> {
        return repository.findAll()
    }

    override fun save(createCommand: CreateListCommand): ShoppingList {
        val shoppingList = ShoppingList(name = createCommand.name)

        return repository.save(shoppingList)
    }

    override fun delete(id: Long) {
        repository.delete(id)
    }
}