package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.team12.teamproject.entity.Holding;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    @Query(value = "SELECT * FROM holdings WHERE account_id = :accountId AND stock_id = :stockId", nativeQuery = true)
    Optional<Holding> findByAccountIdAndStockId(@Param("accountId") Long accountId, @Param("stockId") Long stockId);

    @Modifying
    @Query(value = "DELETE FROM holdings WHERE account_id = :accountId", nativeQuery = true)
    void deleteByAccountId(@Param("accountId") Long accountId);
}
