package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.ItemListRepository;
import com.listaai.domain.model.ItemList;
import com.listaai.infrastructure.adapter.output.persistence.mapper.ItemListPersistenceMapper;
import com.listaai.infrastructure.adapter.output.persistence.repository.ItemListJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ItemListPersistenceAdapter implements ItemListRepository {

    private final ItemListJpaRepository repository;
    private final ItemListPersistenceMapper mapper;

    public ItemListPersistenceAdapter(ItemListJpaRepository repository, ItemListPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<ItemList> getItemsList(long listId) {
        return repository.findAllByListId(listId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public ItemList save(ItemList itemList, long listId) {
        var entity = mapper.toEntity(itemList);
        entity.setListId(listId);
        var saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public ItemList update(ItemList itemList, long listId) {
        var entity = repository.findByIdAndListId(itemList.id(), listId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Item %d not found in list %d".formatted(itemList.id(), listId)));
        entity.setDescription(itemList.description());
        entity.setChecked(itemList.checked());
        entity.setQuantity(itemList.quantity());
        entity.setUom(itemList.uom());
        var saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public void delete(long listId, long id) {
        repository.deleteByIdAndListId(id, listId);
    }
}
