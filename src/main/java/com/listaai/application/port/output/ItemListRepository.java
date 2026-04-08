package com.listaai.application.port.output;

import com.listaai.domain.model.ItemList;

import java.util.List;

public interface ItemListRepository {
    List<ItemList> getItemsList(long listId);
    ItemList save(ItemList itemList, long listId);
    ItemList update(ItemList itemList, long listId);
    void delete(long listId, long id);
}
