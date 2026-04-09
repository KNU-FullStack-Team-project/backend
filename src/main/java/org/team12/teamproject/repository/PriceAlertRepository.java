package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.PriceAlert;
import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {
    List<PriceAlert> findByIsActive(int isActive);
    List<PriceAlert> findByIsActiveAndStockStockCode(int isActive, String stockCode);
    List<PriceAlert> findByUserIdAndIsActive(Long userId, int isActive);

    /**
     * 사용자의 활성화된 알림 목록 조회 (isActive = 1)
     */
    default List<PriceAlert> findByUserIdAndIsActiveTrue(Long userId) {
        return findByUserIdAndIsActive(userId, 1);
    }
}
