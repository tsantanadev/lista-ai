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

    @Column
    private Double quantity;

    @Column
    private String uom;

    protected ItemListEntity() {}

    public ItemListEntity(Long id, Long listId, String description, boolean checked, Double quantity, String uom) {
        this.id = id;
        this.listId = listId;
        this.description = description;
        this.checked = checked;
        this.quantity = quantity;
        this.uom = uom;
    }

    public Long getId() { return id; }
    public Long getListId() { return listId; }
    public void setListId(Long listId) { this.listId = listId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
}
