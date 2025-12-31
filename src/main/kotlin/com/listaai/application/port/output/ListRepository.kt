package com.listaai.application.port.output

import com.listaai.domain.model.ShoppingList

interface ListRepository {
    fun findAll(): List<ShoppingList>
    fun save(list: ShoppingList): ShoppingList
    fun delete(id: Long)
}