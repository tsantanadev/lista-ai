package com.listaai.application.port.input;

import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.domain.model.ItemList;

import java.util.List;

public interface ItemListService {
    List<ItemList> getItemsList(long listId, long userId);
    ItemList save(CreateItemListCommand createCommand, long userId);
    ItemList update(UpdateItemListCommand updateCommand, long userId);
    void delete(long listId, long id, long userId);
}
