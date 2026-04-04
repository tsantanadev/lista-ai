package com.listaai.application.service;

import com.listaai.application.port.input.UserService;
import com.listaai.application.port.output.UserRepository;
import com.listaai.domain.model.User;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public User findById(Long id) {
        // Used by SecurityConfig to load user from JWT subject
        throw new UnsupportedOperationException("Not needed yet — add when required");
    }
}
