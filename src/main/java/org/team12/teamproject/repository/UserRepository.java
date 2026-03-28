package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
}