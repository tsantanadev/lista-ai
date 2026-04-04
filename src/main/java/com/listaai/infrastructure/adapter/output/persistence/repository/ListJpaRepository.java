package com.listaai.infrastructure.adapter.output.persistence.repository;

import com.listaai.infrastructure.adapter.output.persistence.entity.ListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ListJpaRepository extends JpaRepository<ListEntity, Long> {

    @Query("SELECT l FROM ListEntity l WHERE EXISTS " +
           "(SELECT us FROM UserShoppingListEntity us " +
           " WHERE us.id.listId = l.id AND us.id.userId = :userId)")
    List<ListEntity> findAllByUserId(@Param("userId") Long userId);
}
