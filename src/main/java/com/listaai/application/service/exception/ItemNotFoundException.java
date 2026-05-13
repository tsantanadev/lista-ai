package com.listaai.application.service.exception;

public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(long itemId, long listId) {
        super("Item %d not found in list %d".formatted(itemId, listId));
    }
}
