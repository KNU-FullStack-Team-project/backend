package org.team12.teamproject.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.team12.teamproject.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    long countByEmail(@Param("email") String email);

    @Query("SELECT COUNT(u) FROM User u WHERE u.nickname = :nickname")
    long countByNickname(@Param("nickname") String nickname);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);
}