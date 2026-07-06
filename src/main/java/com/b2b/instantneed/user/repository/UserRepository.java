package com.b2b.instantneed.user.repository;

import com.b2b.instantneed.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByPasswordResetTokenHash(String tokenHash);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}
