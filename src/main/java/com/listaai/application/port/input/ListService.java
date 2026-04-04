package com.listaai.application.port.input;

import com.listaai.application.port.input.command.CreateListCommand;
import com.listaai.domain.model.ShoppingList;
import java.util.List;

public interface ListService {
    List<ShoppingList> getAllLists(Long userId);
    ShoppingList createList(CreateListCommand command);
    void deleteList(Long listId, Long userId);
}
