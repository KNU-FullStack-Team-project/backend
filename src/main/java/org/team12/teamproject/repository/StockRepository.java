package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.team12.teamproject.entity.Stock;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByStockCode(String stockCode);

    @Query(value = "SELECT * FROM ( " +
                   "  SELECT a.*, ROWNUM rnum FROM ( " +
                   "    SELECT * FROM stock ORDER BY stock_id ASC " +
                   "  ) a WHERE ROWNUM <= :upper " +
                   ") WHERE rnum > :lower", nativeQuery = true)
    List<Stock> findStocksNative(@Param("lower") int lower, @Param("upper") int upper);
}