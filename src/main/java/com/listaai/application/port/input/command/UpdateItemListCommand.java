package com.listaai.application.port.input.command;

public record UpdateItemListCommand(long id, String description, long listId, boolean checked, Double quantity, String uom) {}
