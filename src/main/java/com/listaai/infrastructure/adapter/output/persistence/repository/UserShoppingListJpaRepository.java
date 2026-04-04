package com.listaai.infrastructure.adapter.output.persistence.repository;

import com.listaai.infrastructure.adapter.output.persistence.entity.UserShoppingListEntity;
import com.listaai.infrastructure.adapter.output.persistence.entity.UserShoppingListId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserShoppingListJpaRepository extends JpaRepository<UserShoppingListEntity, UserShoppingListId> {
    boolean existsByIdUserIdAndIdListId(Long userId, Long listId);
}
