package com.listaai.application.service;

import com.listaai.application.port.input.ListService;
import com.listaai.application.port.input.command.CreateListCommand;
import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ShoppingList;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ListServiceImpl implements ListService {

    private final ListRepository listRepository;

    public ListServiceImpl(ListRepository listRepository) {
        this.listRepository = listRepository;
    }

    @Override
    public List<ShoppingList> getAllLists(Long userId) {
        return listRepository.findAllByUserId(userId);
    }

    @Override
    public ShoppingList createList(CreateListCommand command) {
        return listRepository.save(new ShoppingList(null, command.name()), command.userId());
    }

    @Override
    public void deleteList(Long listId, Long userId) {
        if (!listRepository.existsByIdAndUserId(listId, userId)) {
            throw new AccessDeniedException("You do not own list: " + listId);
        }
        listRepository.deleteById(listId);
    }
}
