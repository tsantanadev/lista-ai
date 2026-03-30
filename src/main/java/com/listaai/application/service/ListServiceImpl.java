package com.listaai.application.service;

import com.listaai.application.port.input.ListService;
import com.listaai.application.port.input.command.CreateListCommand;
import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ShoppingList;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListServiceImpl implements ListService {

    private final ListRepository repository;

    public ListServiceImpl(ListRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ShoppingList> getAll() {
        return repository.findAll();
    }

    @Override
    public ShoppingList save(CreateListCommand createCommand) {
        return repository.save(new ShoppingList(null, createCommand.name()));
    }

    @Override
    public void delete(long id) {
        repository.delete(id);
    }
}
