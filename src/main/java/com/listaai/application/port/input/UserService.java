package com.listaai.application.port.input;

import com.listaai.domain.model.User;
import java.util.Optional;

public interface UserService {
    Optional<User> findByEmail(String email);
    User findById(Long id);
}
