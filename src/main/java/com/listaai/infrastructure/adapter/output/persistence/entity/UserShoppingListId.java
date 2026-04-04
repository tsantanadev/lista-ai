package com.listaai.infrastructure.adapter.output.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserShoppingListId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "list_id")
    private Long listId;

    protected UserShoppingListId() {}

    public UserShoppingListId(Long userId, Long listId) {
        this.userId = userId;
        this.listId = listId;
    }

    public Long getUserId() { return userId; }
    public Long getListId() { return listId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserShoppingListId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(listId, that.listId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, listId); }
}
