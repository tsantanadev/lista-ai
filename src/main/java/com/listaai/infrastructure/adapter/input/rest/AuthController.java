package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.application.port.input.AuthUseCase;
import com.listaai.infrastructure.adapter.input.rest.dto.*;
import com.listaai.infrastructure.adapter.input.rest.mapper.AuthRestMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthUseCase authUseCase;
    private final AuthRestMapper mapper;

    public AuthController(AuthUseCase authUseCase, AuthRestMapper mapper) {
        this.authUseCase = authUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(authUseCase.register(mapper.toCommand(request))));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(mapper.toResponse(authUseCase.loginLocal(mapper.toCommand(request))));
    }

    @PostMapping("/google")
    public ResponseEntity<TokenResponse> google(@RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(mapper.toResponse(authUseCase.loginGoogle(mapper.toCommand(request))));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(mapper.toResponse(authUseCase.refresh(mapper.toCommand(request))));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        authUseCase.logout(mapper.toCommand(request));
        return ResponseEntity.noContent().build();
    }
}
