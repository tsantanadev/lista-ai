package com.listaai.application.port.input

import com.listaai.application.port.input.command.CreateListCommand
import com.listaai.domain.model.ShoppingList

interface ListService {
    fun getAll() : List<ShoppingList>
    fun save(createCommand: CreateListCommand) : ShoppingList
    fun delete(id : Long)
}