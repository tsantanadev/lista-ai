package com.listaai.application.port.output;

import com.listaai.domain.model.ShoppingList;
import java.util.List;

public interface ListRepository {
    List<ShoppingList> findAllByUserId(Long userId);
    ShoppingList save(ShoppingList shoppingList, Long userId);
    boolean existsByIdAndUserId(Long listId, Long userId);
    void deleteById(Long id);
}
