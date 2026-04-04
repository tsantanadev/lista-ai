package com.listaai.application.port.input.command;

// Passed to AuthUseCase and also implements AuthCommand for GoogleAuthProvider
public record GoogleAuthCommand(String idToken) {}
