package com.listaai.application.port.output;

import com.listaai.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
    Optional<UserWithHash> findByEmailWithHash(String email);
    User save(User user, String passwordHash);
    void setVerified(Long userId, boolean verified);

    record UserWithHash(User user, String passwordHash) {}
}
