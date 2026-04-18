package com.listaai.infrastructure.adapter.input.rest.dto;

public record ItemListUpdateRequest(String description, boolean checked, Double quantity, String uom) {}
