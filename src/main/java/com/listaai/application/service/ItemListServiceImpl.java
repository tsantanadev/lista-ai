package com.listaai.application.service;

import com.listaai.application.port.input.ItemListService;
import com.listaai.application.port.input.command.CreateItemListCommand;
import com.listaai.application.port.input.command.UpdateItemListCommand;
import com.listaai.application.port.output.ItemListRepository;
import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ItemList;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemListServiceImpl implements ItemListService {

    private final ItemListRepository repository;
    private final ListRepository listRepository;

    public ItemListServiceImpl(ItemListRepository repository, ListRepository listRepository) {
        this.repository = repository;
        this.listRepository = listRepository;
    }

    @Override
    public List<ItemList> getItemsList(long listId, long userId) {
        checkOwnership(listId, userId);
        return repository.getItemsList(listId);
    }

    @Override
    public ItemList save(CreateItemListCommand createCommand, long userId) {
        checkOwnership(createCommand.listId(), userId);
        var itemList = new ItemList(null, createCommand.description(), false, createCommand.quantity(), createCommand.uom());
        return repository.save(itemList, createCommand.listId());
    }

    @Override
    public ItemList update(UpdateItemListCommand updateCommand, long userId) {
        checkOwnership(updateCommand.listId(), userId);
        var itemList = new ItemList(updateCommand.id(), updateCommand.description(), updateCommand.checked(), updateCommand.quantity(), updateCommand.uom());
        return repository.update(itemList, updateCommand.listId());
    }

    @Override
    public void delete(long listId, long id, long userId) {
        checkOwnership(listId, userId);
        repository.delete(listId, id);
    }

    private void checkOwnership(long listId, long userId) {
        if (!listRepository.existsByIdAndUserId(listId, userId)) {
            throw new AccessDeniedException("You do not own list: " + listId);
        }
    }
}
