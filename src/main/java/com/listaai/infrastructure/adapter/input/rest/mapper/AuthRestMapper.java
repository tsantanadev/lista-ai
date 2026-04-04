package com.listaai.infrastructure.adapter.input.rest.mapper;

import com.listaai.application.port.input.AuthResult;
import com.listaai.application.port.input.command.*;
import com.listaai.infrastructure.adapter.input.rest.dto.*;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthRestMapper {
    RegisterCommand toCommand(RegisterRequest request);
    LoginCommand toCommand(LoginRequest request);
    GoogleAuthCommand toCommand(GoogleAuthRequest request);
    RefreshCommand toCommand(RefreshRequest request);
    TokenResponse toResponse(AuthResult result);
}
