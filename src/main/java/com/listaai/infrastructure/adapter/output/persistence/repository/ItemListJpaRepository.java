package com.listaai.infrastructure.adapter.output.persistence.repository;

import com.listaai.infrastructure.adapter.output.persistence.entity.ItemListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemListJpaRepository extends JpaRepository<ItemListEntity, Long> {
    List<ItemListEntity> findAllByListId(long listId);

    @Transactional
    void deleteByIdAndListId(long id, long listId);

    Optional<ItemListEntity> findByIdAndListId(long id, long listId);
}
