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
                   "    AND (:industry IS NULL OR industry = :industry) " +
                   "    ORDER BY (current_price * volume) DESC NULLS LAST, stock_id ASC " +
                   "  ) a WHERE ROWNUM <= :upper " +
                   ") WHERE rnum > :lower", nativeQuery = true)
    List<Stock> findStocksNative(@Param("lower") int lower, @Param("upper") int upper, @Param("industry") String industry);

    @Query(value = "SELECT * FROM ( " +
                   "  SELECT a.*, ROWNUM rnum FROM ( " +
                   "    SELECT * FROM stock " +
                   "    WHERE market_type != 'UNKNOWN' " +
                   "    AND (:industry IS NULL OR industry = :industry) " +
                   "    AND (:stockType IS NULL OR stock_type = :stockType) " +
                   "    ORDER BY (current_price * volume) DESC NULLS LAST, stock_id ASC " +
                   "  ) a WHERE ROWNUM <= :upper " +
                   ") WHERE rnum > :lower", nativeQuery = true)
    List<Stock> findStocksNativeWithFilters(@Param("lower") int lower, @Param("upper") int upper, 
                                           @Param("industry") String industry, @Param("stockType") String stockType);

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.marketType != 'UNKNOWN' " +
           "AND (:industry IS NULL OR s.industry = :industry) " +
           "AND (:stockType IS NULL OR s.stockType = :stockType)")
    long countValidStocksWithFilters(@Param("industry") String industry, @Param("stockType") String stockType);

    @Query(value = "SELECT DISTINCT s.stock_code " +
                   "FROM stock s " +
                   "WHERE s.stock_id IN (SELECT stock_id FROM favorite_stocks) " +
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

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.marketType != 'UNKNOWN' AND LENGTH(s.stockCode) = 6 AND (:industry IS NULL OR s.industry = :industry)")
    long countValidStocks(@Param("industry") String industry);

    @Query("SELECT DISTINCT s.industry FROM Stock s WHERE s.industry IS NOT NULL ORDER BY s.industry ASC")
    List<String> findAllIndustries();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Stock s SET s.currentPrice = :price, s.changeRate = :rate, s.changeAmount = :amt, s.volume = :volume WHERE s.stockCode = :stockCode")
    void updateStockPriceInfo(@Param("stockCode") String stockCode, @Param("price") BigDecimal price, @Param("rate") BigDecimal rate, @Param("amt") BigDecimal amt, @Param("volume") Long volume);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Stock s SET s.volume = :volume WHERE s.stockCode = :stockCode")
    void updateStockVolume(@Param("stockCode") String stockCode, @Param("volume") Long volume);
}