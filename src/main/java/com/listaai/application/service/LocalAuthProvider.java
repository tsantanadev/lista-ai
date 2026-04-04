package com.listaai.application.service;

import com.listaai.application.port.input.AuthIdentity;
import com.listaai.application.port.input.AuthProvider;
import com.listaai.application.port.input.command.LoginCommand;
import com.listaai.application.port.output.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class LocalAuthProvider implements AuthProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalAuthProvider(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String providerName() {
        return "local";
    }

    @Override
    public AuthIdentity authenticate(Object command) {
        if (!(command instanceof LoginCommand cmd)) {
            throw new IllegalArgumentException("Expected LoginCommand");
        }
        UserRepository.UserWithHash userWithHash = userRepository.findByEmailWithHash(cmd.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(cmd.password(), userWithHash.passwordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return new AuthIdentity(
                userWithHash.user().email(),
                userWithHash.user().name(),
                null // no provider user ID for local auth
        );
    }
}
