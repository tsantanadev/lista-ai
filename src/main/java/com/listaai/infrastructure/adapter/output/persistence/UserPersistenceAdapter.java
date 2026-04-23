package com.listaai.infrastructure.adapter.output.persistence;

import com.listaai.application.port.output.UserRepository;
import com.listaai.domain.model.User;
import com.listaai.infrastructure.adapter.output.persistence.entity.UserEntity;
import com.listaai.infrastructure.adapter.output.persistence.mapper.UserPersistenceMapper;
import com.listaai.infrastructure.adapter.output.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
    public Optional<UserRepository.UserWithHash> findByEmailWithHash(String email) {
        return jpaRepository.findByEmail(email)
                .map(e -> new UserRepository.UserWithHash(mapper.toDomain(e), e.getPasswordHash()));
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public User save(User user, String passwordHash) {
        UserEntity entity = new UserEntity(user.email(), user.name(), passwordHash, user.verified());
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void setVerified(Long userId, boolean verified) {
        jpaRepository.findById(userId).ifPresent(e -> e.setVerified(verified));
    }
}
