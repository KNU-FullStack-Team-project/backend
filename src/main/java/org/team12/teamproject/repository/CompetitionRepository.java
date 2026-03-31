package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.team12.teamproject.entity.Competition;

import java.util.List;
import java.util.Optional;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    @Query(value = "SELECT * FROM competitions ORDER BY competition_id", nativeQuery = true)
    List<Competition> findAllCompetitionsNative();

    @Query(value = "SELECT * FROM competitions WHERE competition_id = :competitionId", nativeQuery = true)
    Optional<Competition> findCompetitionByIdNative(@Param("competitionId") Long competitionId);
}