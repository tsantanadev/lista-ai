package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.ListRepository;
import com.listaai.domain.model.ShoppingList;
import com.listaai.infrastructure.adapter.output.persistence.entity.ListEntity;
import com.listaai.infrastructure.adapter.output.persistence.entity.UserShoppingListEntity;
import com.listaai.infrastructure.adapter.output.persistence.mapper.ListPersistenceMapper;
import com.listaai.infrastructure.adapter.output.persistence.repository.ListJpaRepository;
import com.listaai.infrastructure.adapter.output.persistence.repository.UserShoppingListJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
public class ListPersistenceAdapter implements ListRepository {

    private final ListJpaRepository listJpaRepository;
    private final UserShoppingListJpaRepository userShoppingListJpaRepository;
    private final ListPersistenceMapper mapper;

    public ListPersistenceAdapter(ListJpaRepository listJpaRepository,
                                  UserShoppingListJpaRepository userShoppingListJpaRepository,
                                  ListPersistenceMapper mapper) {
        this.listJpaRepository = listJpaRepository;
        this.userShoppingListJpaRepository = userShoppingListJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ShoppingList> findAllByUserId(Long userId) {
        return listJpaRepository.findAllByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public ShoppingList save(ShoppingList shoppingList, Long userId) {
        ListEntity saved = listJpaRepository.save(mapper.toEntity(shoppingList));
        userShoppingListJpaRepository.save(
                new UserShoppingListEntity(userId, saved.getId(), "owner"));
        return mapper.toDomain(saved);
    }

    @Override
    public boolean existsByIdAndUserId(Long listId, Long userId) {
        return userShoppingListJpaRepository.existsByIdUserIdAndIdListId(userId, listId);
    }

    @Override
    public void deleteById(Long id) {
        listJpaRepository.deleteById(id);
    }
}
