package com.listaai.infrastructure.adapter.input.rest;

import com.listaai.application.port.input.AuthUseCase;
import com.listaai.application.port.input.command.VerifyEmailCommand;
import com.listaai.infrastructure.adapter.input.rest.dto.*;
import com.listaai.infrastructure.adapter.input.rest.mapper.AuthRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "User registration, login, token refresh, and logout")
public class AuthController {

    private final AuthUseCase authUseCase;
    private final AuthRestMapper mapper;

    public AuthController(AuthUseCase authUseCase, AuthRestMapper mapper) {
        this.authUseCase = authUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
               description = "Creates a new user account and returns a JWT access token and refresh token.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully",
            content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content)
    })
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(authUseCase.register(mapper.toCommand(request))));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password",
               description = "Authenticates a user with local credentials and returns JWT tokens.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid email or password",
            content = @Content)
    })
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(mapper.toResponse(authUseCase.loginLocal(mapper.toCommand(request))));
    }

    @PostMapping("/google")
    @Operation(summary = "Login with Google",
               description = "Authenticates a user using a Google ID token obtained from Google Sign-In. Creates the account if it does not exist.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or expired Google ID token",
            content = @Content)
    })
    public ResponseEntity<TokenResponse> google(@RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(mapper.toResponse(authUseCase.loginGoogle(mapper.toCommand(request))));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token",
               description = "Issues a new access token using a valid refresh token. The refresh token is rotated.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
            content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Refresh token is invalid or expired",
            content = @Content)
    })
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(mapper.toResponse(authUseCase.refresh(mapper.toCommand(request))));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout",
               description = "Invalidates the provided refresh token. Subsequent refresh attempts with this token will fail.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out successfully"),
        @ApiResponse(responseCode = "401", description = "Refresh token is invalid",
            content = @Content)
    })
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        authUseCase.logout(mapper.toCommand(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify an email address",
               description = "Consumes a verification token previously emailed to the user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verified (or already verified — idempotent)"),
        @ApiResponse(responseCode = "400", description = "Token is invalid", content = @Content),
        @ApiResponse(responseCode = "410", description = "Token expired or superseded", content = @Content)
    })
    public ResponseEntity<Void> verifyEmail(@RequestBody VerifyEmailRequest request) {
        authUseCase.verifyEmail(new VerifyEmailCommand(request.token()));
        return ResponseEntity.ok().build();
    }
}
