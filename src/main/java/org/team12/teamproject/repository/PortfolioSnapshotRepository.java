package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.team12.teamproject.entity.PortfolioSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {
    
    // 특정 계좌의 기간별 스냅샷 조회
    List<PortfolioSnapshot> findByAccountIdOrderBySnapshotDateAsc(Long accountId);
    
    // 특정 날짜의 스냅샷이 이미 존재하는지 확인
    Optional<PortfolioSnapshot> findByAccountIdAndSnapshotDate(Long accountId, LocalDate snapshotDate);
}
