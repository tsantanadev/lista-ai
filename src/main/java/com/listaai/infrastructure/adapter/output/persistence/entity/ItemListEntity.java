package com.listaai.infrastructure.adapter.output.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "item_list")
public class ItemListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long listId;

    @Column
    private String description;

    @Column
    private boolean checked;

    protected ItemListEntity() {}

    public ItemListEntity(Long id, Long listId, String description, boolean checked) {
        this.id = id;
        this.listId = listId;
        this.description = description;
        this.checked = checked;
    }

    public Long getId() { return id; }
    public Long getListId() { return listId; }
    public void setListId(Long listId) { this.listId = listId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
}
