package com.listaai.application.service;

import com.listaai.application.port.input.ItemListService;
import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.application.port.output.ItemListRepository;
import com.listaai.domain.model.ItemList;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemListServiceImpl implements ItemListService {

    private final ItemListRepository repository;

    public ItemListServiceImpl(ItemListRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ItemList> getItemsList(long listId) {
        return repository.getItemsList(listId);
    }

    @Override
    public ItemList save(CreateItemListCommand createCommand) {
        var itemList = new ItemList(null, createCommand.description(), false);
        return repository.save(itemList, createCommand.listId());
    }

    @Override
    public ItemList update(UpdateItemListCommand updateCommand) {
        var itemList = new ItemList(updateCommand.id(), updateCommand.description(), updateCommand.checked());
        return repository.update(itemList, updateCommand.listId());
    }

    @Override
    public void delete(long listId, long id) {
        repository.delete(listId, id);
    }
}
