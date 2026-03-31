package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}