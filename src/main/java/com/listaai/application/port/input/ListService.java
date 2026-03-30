package com.listaai.application.port.input;

import com.listaai.application.port.input.command.CreateListCommand;
import com.listaai.domain.model.ShoppingList;

import java.util.List;

public interface ListService {
    List<ShoppingList> getAll();
    ShoppingList save(CreateListCommand createCommand);
    void delete(long id);
}
