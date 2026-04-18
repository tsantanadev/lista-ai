package com.listaai.application.port.input.command;

public record CreateItemListCommand(String description, long listId, Double quantity, String uom) {}
