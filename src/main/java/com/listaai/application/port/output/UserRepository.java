package com.listaai.application.port.output;

import com.listaai.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    User save(User user, String passwordHash);
}
