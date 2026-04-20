package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.team12.teamproject.entity.Stock;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByStockCode(String stockCode);
    List<Stock> findAllByStockCodeIn(List<String> stockCodes);

    @Query(value = "SELECT * FROM ( " +
                   "  SELECT a.*, ROWNUM rnum FROM ( " +
                   "    SELECT * FROM stock " +
                   "    WHERE market_type != 'UNKNOWN' AND LENGTH(stock_code) = 6 " +
                   "    ORDER BY stock_id ASC " +
                   "  ) a WHERE ROWNUM <= :upper " +
                   ") WHERE rnum > :lower", nativeQuery = true)
    List<Stock> findStocksNative(@Param("lower") int lower, @Param("upper") int upper);

    @Query(value = "SELECT DISTINCT s.stock_code " +
                   "FROM stock s " +
                   "WHERE s.stock_code IN (SELECT stock_symbol FROM favorite_stocks) " +
                   "OR s.stock_id IN (SELECT stock_id FROM holdings)", nativeQuery = true)
    List<String> findAllActiveStockCodes();

    @Query(value = "SELECT * FROM ( " +
                   "  SELECT a.*, ROWNUM rnum FROM ( " +
                   "    SELECT * FROM stock " +
                   "    WHERE (stock_name LIKE %:keyword% OR stock_code LIKE %:keyword%) " +
                   "    AND market_type != 'UNKNOWN' AND LENGTH(stock_code) = 6 " +
                   "    ORDER BY stock_name ASC " +
                   "  ) a WHERE ROWNUM <= 50 " +
                   ") WHERE rnum > 0", nativeQuery = true)
    List<Stock> searchStocks(@Param("keyword") String keyword);

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.marketType != 'UNKNOWN' AND LENGTH(s.stockCode) = 6")
    long countValidStocks();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Stock s SET s.currentPrice = :price, s.changeRate = :rate, s.changeAmount = :amt, s.volume = :volume WHERE s.stockCode = :stockCode")
    void updateStockPriceInfo(@Param("stockCode") String stockCode, @Param("price") BigDecimal price, @Param("rate") BigDecimal rate, @Param("amt") BigDecimal amt, @Param("volume") Long volume);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Stock s SET s.volume = :volume WHERE s.stockCode = :stockCode")
    void updateStockVolume(@Param("stockCode") String stockCode, @Param("volume") Long volume);
}