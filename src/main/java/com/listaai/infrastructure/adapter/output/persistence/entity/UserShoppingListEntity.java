package com.listaai.infrastructure.adapter.output.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_shopping_list")
public class UserShoppingListEntity {

    @EmbeddedId
    private UserShoppingListId id;

    @Column(nullable = false)
    private String role;

    protected UserShoppingListEntity() {}

    public UserShoppingListEntity(Long userId, Long listId, String role) {
        this.id = new UserShoppingListId(userId, listId);
        this.role = role;
    }

    public UserShoppingListId getId() { return id; }
    public String getRole() { return role; }
}
