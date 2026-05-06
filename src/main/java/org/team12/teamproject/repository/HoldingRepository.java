package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.team12.teamproject.entity.Holding;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    @Query(value = "SELECT * FROM holdings WHERE account_id = :accountId AND stock_id = :stockId", nativeQuery = true)
    Optional<Holding> findByAccountIdAndStockId(
            @Param("accountId") Long accountId,
            @Param("stockId") Long stockId
    );
 // [최적화] JOIN FETCH를 사용하여 Stock 정보를 한꺼번에 가져옴으로써 병렬 처리 성능과 안정성 확보
    @Query("SELECT h FROM Holding h JOIN FETCH h.stock WHERE h.account.id = :accountId")
    List<Holding> findByAccountId(@Param("accountId") Long accountId);

    @Modifying
    @Query(value = "DELETE FROM holdings WHERE account_id = :accountId", nativeQuery = true)
    void deleteByAccountId(@Param("accountId") Long accountId);

    @Query("""
        SELECT COUNT(h)
        FROM Holding h
        WHERE h.account.user.id = :userId
          AND h.quantity > 0
    """)
    long countActiveHoldingsByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(h)
        FROM Holding h
        WHERE h.account.user.id = :userId
          AND h.quantity > 0
          AND h.stock.currentPrice IS NOT NULL
          AND h.stock.currentPrice > h.averageBuyPrice
    """)
    long countProfitHoldingsByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(h)
        FROM Holding h
        WHERE h.account.user.id = :userId
          AND h.quantity > 0
          AND h.stock.currentPrice IS NOT NULL
          AND h.averageBuyPrice > 0
          AND ((h.stock.currentPrice - h.averageBuyPrice) / h.averageBuyPrice) >= 0.2
    """)
    long countHighProfitHoldingsByUserId(@Param("userId") Long userId);
}