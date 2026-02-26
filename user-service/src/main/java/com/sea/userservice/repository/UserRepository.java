package com.sea.userservice.repository;

import com.sea.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    boolean existsByKeycloakId(String keycloakId);

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByEmail(String email);
}
