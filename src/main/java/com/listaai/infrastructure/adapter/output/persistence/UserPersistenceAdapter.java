package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.UserRepository;
import com.listaai.domain.model.User;
import com.listaai.infrastructure.adapter.output.persistence.entity.UserEntity;
import com.listaai.infrastructure.adapter.output.persistence.mapper.UserPersistenceMapper;
import com.listaai.infrastructure.adapter.output.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    public UserPersistenceAdapter(UserJpaRepository jpaRepository, UserPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public User save(User user, String passwordHash) {
        UserEntity entity = new UserEntity(user.email(), user.name(), passwordHash);
        return mapper.toDomain(jpaRepository.save(entity));
    }
}
