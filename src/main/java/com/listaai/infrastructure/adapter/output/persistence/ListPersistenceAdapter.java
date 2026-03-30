package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ShoppingList;
import com.listaai.infrastructure.adapter.output.persistence.mapper.ListPersistenceMapper;
import com.listaai.infrastructure.adapter.output.persistence.repository.ListJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ListPersistenceAdapter implements ListRepository {

    private final ListJpaRepository repository;
    private final ListPersistenceMapper mapper;

    public ListPersistenceAdapter(ListJpaRepository repository, ListPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<ShoppingList> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public ShoppingList save(ShoppingList list) {
        var entity = mapper.toEntity(list);
        var persisted = repository.save(entity);
        return mapper.toDomain(persisted);
    }

    @Override
    public void delete(long id) {
        repository.deleteById(id);
    }
}
