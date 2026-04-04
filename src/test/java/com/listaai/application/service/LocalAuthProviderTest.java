package com.listaai.application.service;

import com.listaai.application.port.input.AuthIdentity;
import com.listaai.application.port.input.command.LoginCommand;
import com.listaai.application.port.output.UserRepository;
import com.listaai.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalAuthProviderTest {

    @Mock
    private UserRepository userRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private LocalAuthProvider localAuthProvider;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        localAuthProvider = new LocalAuthProvider(userRepository, passwordEncoder);
    }

    @Test
    void providerName_returnsLocal() {
        assertThat(localAuthProvider.providerName()).isEqualTo("local");
    }

    @Test
    void authenticate_validCredentials_returnsAuthIdentity() {
        String rawPassword = "Password123!";
        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(1L, "user@example.com", "Test User");
        UserRepository.UserWithHash userWithHash = new UserRepository.UserWithHash(user, hash);
        when(userRepository.findByEmailWithHash("user@example.com"))
                .thenReturn(Optional.of(userWithHash));
        AuthIdentity identity = localAuthProvider.authenticate(new LoginCommand("user@example.com", rawPassword));
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.name()).isEqualTo("Test User");
        assertThat(identity.providerUserId()).isNull();
    }

    @Test
    void authenticate_wrongPassword_throwsBadCredentials() {
        User user = new User(1L, "user@example.com", "Test User");
        String hash = passwordEncoder.encode("correct-password");
        UserRepository.UserWithHash userWithHash = new UserRepository.UserWithHash(user, hash);
        when(userRepository.findByEmailWithHash("user@example.com"))
                .thenReturn(Optional.of(userWithHash));
        assertThatThrownBy(() ->
                localAuthProvider.authenticate(new LoginCommand("user@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticate_unknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmailWithHash("unknown@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() ->
                localAuthProvider.authenticate(new LoginCommand("unknown@example.com", "password")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
