package com.listaai.infrastructure.adapter.output.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserEntity() {}

    public UserEntity(String email, String name, String passwordHash, boolean verified) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.verified = verified;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public Instant getCreatedAt() { return createdAt; }
}
