package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.Holding;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    Optional<Holding> findByAccountIdAndStockId(Long accountId, Long stockId);
}
