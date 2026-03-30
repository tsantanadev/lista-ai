package com.listaai.application.port.output;

import com.listaai.domain.model.ShoppingList;

import java.util.List;

public interface ListRepository {
    List<ShoppingList> findAll();
    ShoppingList save(ShoppingList list);
    void delete(long id);
}
