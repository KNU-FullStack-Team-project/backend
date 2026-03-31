package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.CompetitionDetailResponseDto;
import org.team12.teamproject.dto.CompetitionListResponseDto;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitionService {

    private final JdbcTemplate jdbcTemplate;

    public List<CompetitionListResponseDto> getCompetitionList() {
        String sql = """
                SELECT c.competition_id,
                       c.title,
                       c.description,
                       c.status,
                       c.start_at,
                       c.end_at,
                       COUNT(cp.competition_id) AS participant_count
                FROM competitions c
                LEFT JOIN competition_participants cp
                  ON c.competition_id = cp.competition_id
                GROUP BY c.competition_id, c.title, c.description, c.status, c.start_at, c.end_at
                ORDER BY c.competition_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CompetitionListResponseDto(
                rs.getLong("competition_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("start_at")),
                toLocalDateTime(rs.getTimestamp("end_at")),
                rs.getInt("participant_count")
        ));
    }

    public CompetitionDetailResponseDto getCompetitionDetail(Long competitionId) {
        String sql = """
                SELECT c.competition_id,
                       c.title,
                       c.description,
                       c.status,
                       c.start_at,
                       c.end_at,
                       c.initial_seed_money,
                       c.max_participants,
                       COUNT(cp.competition_id) AS participant_count
                FROM competitions c
                LEFT JOIN competition_participants cp
                  ON c.competition_id = cp.competition_id
                WHERE c.competition_id = ?
                GROUP BY c.competition_id, c.title, c.description, c.status,
                         c.start_at, c.end_at, c.initial_seed_money, c.max_participants
                """;

        List<CompetitionDetailResponseDto> result = jdbcTemplate.query(
                sql,
                new Object[]{competitionId},
                (rs, rowNum) -> new CompetitionDetailResponseDto(
                        rs.getLong("competition_id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("start_at")),
                        toLocalDateTime(rs.getTimestamp("end_at")),
                        rs.getBigDecimal("initial_seed_money"),
                        rs.getObject("max_participants") != null ? rs.getInt("max_participants") : null,
                        rs.getInt("participant_count")
                )
        );

        return result.stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("존재하지 않는 대회입니다."));
    }

    @Transactional
    public void joinCompetition(Long competitionId, Long userId) {
        String checkSql = """
                SELECT COUNT(*)
                FROM competition_participants
                WHERE competition_id = ? AND user_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                checkSql,
                Integer.class,
                competitionId,
                userId
        );

        if (count != null && count > 0) {
            throw new RuntimeException("이미 참가한 대회입니다.");
        }

        String competitionSql = """
                SELECT title, initial_seed_money, status
                FROM competitions
                WHERE competition_id = ?
                """;

        Map<String, Object> competition = jdbcTemplate.queryForMap(
                competitionSql,
                competitionId
        );

        String title = (String) competition.get("TITLE");
        BigDecimal initialSeedMoney = (BigDecimal) competition.get("INITIAL_SEED_MONEY");
        String status = (String) competition.get("STATUS");

        if ("ENDED".equalsIgnoreCase(status)) {
            throw new RuntimeException("종료된 대회에는 참가할 수 없습니다.");
        }

        Long accountId = jdbcTemplate.queryForObject(
                "SELECT ACCOUNTS_SEQ.NEXTVAL FROM dual",
                Long.class
        );

        String insertAccountSql = """
                INSERT INTO accounts (
                    account_id,
                    account_name,
                    account_type,
                    cash_balance,
                    competition_id,
                    created_at,
                    is_active,
                    updated_at,
                    user_id
                ) VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP, ?, SYSTIMESTAMP, ?)
                """;

        jdbcTemplate.update(
                insertAccountSql,
                accountId,
                title + " 참가 계좌",
                "COMPETITION",
                initialSeedMoney,
                competitionId,
                1,
                userId
        );

        Long participantId = jdbcTemplate.queryForObject(
                "SELECT COMPETITION_PARTICIPANTS_SEQ.NEXTVAL FROM dual",
                Long.class
        );

        String insertParticipantSql = """
                INSERT INTO competition_participants (
                    competition_participant_id,
                    competition_id,
                    user_id,
                    account_id,
                    joined_at,
                    participation_status
                ) VALUES (?, ?, ?, ?, SYSDATE, ?)
                """;

        jdbcTemplate.update(
                insertParticipantSql,
                participantId,
                competitionId,
                userId,
                accountId,
                "JOINED"
        );
    }

    public List<Long> getMyCompetitionIds(Long userId) {
        String sql = """
                SELECT competition_id
                FROM competition_participants
                WHERE user_id = ?
                ORDER BY competition_id
                """;

        return jdbcTemplate.query(
                sql,
                new Object[]{userId},
                (rs, rowNum) -> rs.getLong("competition_id")
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}