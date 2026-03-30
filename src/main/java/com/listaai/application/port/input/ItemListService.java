package com.listaai.application.port.input;

import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.domain.model.ItemList;

import java.util.List;

public interface ItemListService {
    List<ItemList> getItemsList(long listId);
    void save(CreateItemListCommand createCommand);
    ItemList update(UpdateItemListCommand updateCommand);
    void delete(long listId, long id);
}
