package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.team12.teamproject.entity.Account;
import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {
    
    @Query(value = "SELECT * FROM accounts WHERE user_id = :userId", nativeQuery = true)
    List<Account> findByUserId(@Param("userId") Long userId);
}
