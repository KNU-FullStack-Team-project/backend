package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.CompetitionDetailResponseDto;
import org.team12.teamproject.dto.CompetitionListResponseDto;
import org.team12.teamproject.dto.CompetitionParticipantDto;
import org.team12.teamproject.dto.CompetitionRankingResponseDto;
import org.team12.teamproject.dto.CompetitionSaveRequestDto;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitionService {

    private final JdbcTemplate jdbcTemplate;

    public List<CompetitionListResponseDto> getCompetitionList(boolean isAdmin) {
        String sql = """
                SELECT c.competition_id,
                       c.title,
                       c.description,
                       CASE
                           WHEN c.status = 'CANCELED' THEN 'CANCELED'
                           WHEN SYSTIMESTAMP < c.start_at THEN 'SCHEDULED'
                           WHEN SYSTIMESTAMP > c.end_at THEN 'ENDED'
                           ELSE 'ONGOING'
                       END AS status,
                       c.start_at,
                       c.end_at,
                       c.initial_seed_money,
                       c.max_participants,
                       c.is_public,
                       COUNT(CASE WHEN cp.participation_status = 'JOINED' THEN 1 END) AS participant_count
                FROM competitions c
                LEFT JOIN competition_participants cp
                  ON c.competition_id = cp.competition_id
                WHERE (? = 1 OR c.is_public = 1)
                GROUP BY c.competition_id,
                         c.title,
                         c.description,
                         c.status,
                         c.start_at,
                         c.end_at,
                         c.initial_seed_money,
                         c.max_participants,
                         c.is_public
                ORDER BY c.competition_id DESC
                """;

        return jdbcTemplate.query(sql, ps -> {
            ps.setInt(1, isAdmin ? 1 : 0);
        }, (rs, rowNum) -> new CompetitionListResponseDto(
                rs.getLong("competition_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("start_at")),
                toLocalDateTime(rs.getTimestamp("end_at")),
                rs.getBigDecimal("initial_seed_money"),
                rs.getObject("max_participants") != null ? rs.getInt("max_participants") : null,
                rs.getInt("participant_count"),
                rs.getInt("is_public") == 1
        ));
    }

    public CompetitionDetailResponseDto getCompetitionDetail(Long competitionId, boolean isAdmin) {
        String sql = """
                SELECT c.competition_id,
                       c.title,
                       c.description,
                       CASE
                           WHEN c.status = 'CANCELED' THEN 'CANCELED'
                           WHEN SYSTIMESTAMP < c.start_at THEN 'SCHEDULED'
                           WHEN SYSTIMESTAMP > c.end_at THEN 'ENDED'
                           ELSE 'ONGOING'
                       END AS status,
                       c.start_at,
                       c.end_at,
                       c.initial_seed_money,
                       c.max_participants,
                       c.is_public,
                       COUNT(CASE WHEN cp.participation_status = 'JOINED' THEN 1 END) AS participant_count
                FROM competitions c
                LEFT JOIN competition_participants cp
                  ON c.competition_id = cp.competition_id
                WHERE c.competition_id = ?
                  AND (? = 1 OR c.is_public = 1)
                GROUP BY c.competition_id,
                         c.title,
                         c.description,
                         c.status,
                         c.start_at,
                         c.end_at,
                         c.initial_seed_money,
                         c.max_participants,
                         c.is_public
                """;

        List<CompetitionDetailResponseDto> result = jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setLong(1, competitionId);
                    ps.setInt(2, isAdmin ? 1 : 0);
                },
                (rs, rowNum) -> new CompetitionDetailResponseDto(
                        rs.getLong("competition_id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("status"),
                        toLocalDateTime(rs.getTimestamp("start_at")),
                        toLocalDateTime(rs.getTimestamp("end_at")),
                        rs.getBigDecimal("initial_seed_money"),
                        rs.getObject("max_participants") != null ? rs.getInt("max_participants") : null,
                        rs.getInt("participant_count"),
                        rs.getInt("is_public") == 1
                )
        );

        return result.stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("존재하지 않거나 비공개된 대회입니다."));
    }

    @Transactional
    public void joinCompetition(Long competitionId, Long userId) {
        String checkSql = """
                SELECT COUNT(*)
                FROM competition_participants
                WHERE competition_id = ? AND user_id = ?
                  AND participation_status = 'JOINED'
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
                SELECT title,
                       initial_seed_money,
                       is_public,
                       CASE
                           WHEN status = 'CANCELED' THEN 'CANCELED'
                           WHEN SYSTIMESTAMP < start_at THEN 'SCHEDULED'
                           WHEN SYSTIMESTAMP > end_at THEN 'ENDED'
                           ELSE 'ONGOING'
                       END AS display_status
                FROM competitions
                WHERE competition_id = ?
                """;

        List<Map<String, Object>> competitions = jdbcTemplate.queryForList(competitionSql, competitionId);

        if (competitions.isEmpty()) {
            throw new RuntimeException("존재하지 않는 대회입니다.");
        }

        Map<String, Object> competition = competitions.get(0);

        String title = (String) competition.get("TITLE");
        BigDecimal initialSeedMoney = (BigDecimal) competition.get("INITIAL_SEED_MONEY");
        String status = (String) competition.get("DISPLAY_STATUS");
        Integer isPublic = competition.get("IS_PUBLIC") == null
                ? 1
                : ((Number) competition.get("IS_PUBLIC")).intValue();

        if (isPublic != 1) {
            throw new RuntimeException("비공개 대회에는 참가할 수 없습니다.");
        }

        if ("CANCELED".equalsIgnoreCase(status)) {
            throw new RuntimeException("취소된 대회에는 참가할 수 없습니다.");
        }

        if ("SCHEDULED".equalsIgnoreCase(status)) {
            throw new RuntimeException("아직 시작되지 않은 대회에는 참가할 수 없습니다.");
        }

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
                  AND participation_status = 'JOINED'
                ORDER BY competition_id
                """;

        return jdbcTemplate.query(
                sql,
                new Object[]{userId},
                (rs, rowNum) -> rs.getLong("competition_id")
        );
    }

    @Transactional
    public Long createCompetition(Long adminUserId, CompetitionSaveRequestDto request) {
        validateSaveRequest(request);

        Long competitionId = jdbcTemplate.queryForObject(
                "SELECT COMPETITIONS_SEQ.NEXTVAL FROM dual",
                Long.class
        );

        String status = resolveStatus(request.getStartAt(), request.getEndAt());

        String sql = """
                INSERT INTO competitions (
                    competition_id,
                    title,
                    description,
                    start_at,
                    end_at,
                    initial_seed_money,
                    max_participants,
                    status,
                    is_public,
                    created_by_admin_id,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, SYSTIMESTAMP, SYSTIMESTAMP)
                """;

        jdbcTemplate.update(
                sql,
                competitionId,
                request.getTitle().trim(),
                request.getDescription(),
                Timestamp.valueOf(request.getStartAt()),
                Timestamp.valueOf(request.getEndAt()),
                request.getInitialSeedMoney(),
                request.getMaxParticipants(),
                status,
                adminUserId
        );

        return competitionId;
    }

    @Transactional
    public void updateCompetition(Long competitionId, CompetitionSaveRequestDto request) {
        validateSaveRequest(request);

        String existsSql = "SELECT COUNT(*) FROM competitions WHERE competition_id = ?";
        Integer exists = jdbcTemplate.queryForObject(existsSql, Integer.class, competitionId);

        if (exists == null || exists == 0) {
            throw new RuntimeException("존재하지 않는 대회입니다.");
        }

        Integer participantCount = getJoinedParticipantCount(competitionId);

        Map<String, Object> current = jdbcTemplate.queryForMap(
                """
                SELECT title, description, start_at, end_at, initial_seed_money, max_participants, status
                FROM competitions
                WHERE competition_id = ?
                """,
                competitionId
        );

        LocalDateTime currentStartAt = toLocalDateTime((Timestamp) current.get("START_AT"));
        LocalDateTime currentEndAt = toLocalDateTime((Timestamp) current.get("END_AT"));
        BigDecimal currentInitialSeedMoney = (BigDecimal) current.get("INITIAL_SEED_MONEY");
        Integer currentMaxParticipants = current.get("MAX_PARTICIPANTS") != null
                ? ((Number) current.get("MAX_PARTICIPANTS")).intValue()
                : null;
        String currentStatus = (String) current.get("STATUS");

        if ("CANCELED".equalsIgnoreCase(currentStatus)) {
            throw new RuntimeException("취소된 대회는 수정할 수 없습니다.");
        }

        if (participantCount != null && participantCount > 0) {
            boolean changedRestrictedField =
                    !currentStartAt.equals(request.getStartAt()) ||
                    !currentEndAt.equals(request.getEndAt()) ||
                    currentInitialSeedMoney.compareTo(request.getInitialSeedMoney()) != 0 ||
                    !equalsInteger(currentMaxParticipants, request.getMaxParticipants());

            if (changedRestrictedField) {
                throw new RuntimeException("참가자가 있는 대회는 시작일, 종료일, 시드머니, 최대 참가자 수를 수정할 수 없습니다.");
            }

            String sql = """
                    UPDATE competitions
                    SET title = ?,
                        description = ?,
                        updated_at = SYSTIMESTAMP
                    WHERE competition_id = ?
                    """;

            jdbcTemplate.update(
                    sql,
                    request.getTitle().trim(),
                    request.getDescription(),
                    competitionId
            );

            return;
        }

        String status = resolveStatus(request.getStartAt(), request.getEndAt());

        String sql = """
                UPDATE competitions
                SET title = ?,
                    description = ?,
                    start_at = ?,
                    end_at = ?,
                    initial_seed_money = ?,
                    max_participants = ?,
                    status = ?,
                    updated_at = SYSTIMESTAMP
                WHERE competition_id = ?
                """;

        jdbcTemplate.update(
                sql,
                request.getTitle().trim(),
                request.getDescription(),
                Timestamp.valueOf(request.getStartAt()),
                Timestamp.valueOf(request.getEndAt()),
                request.getInitialSeedMoney(),
                request.getMaxParticipants(),
                status,
                competitionId
        );
    }

    @Transactional
    public void updateCompetitionVisibility(Long competitionId, boolean isPublic) {
        Map<String, Object> competition = getCompetitionRow(competitionId);

        String displayStatus = resolveDisplayStatus(
                (String) competition.get("STATUS"),
                toLocalDateTime((Timestamp) competition.get("START_AT")),
                toLocalDateTime((Timestamp) competition.get("END_AT"))
        );

        if ("ONGOING".equalsIgnoreCase(displayStatus)) {
            throw new RuntimeException("진행중인 대회는 공개/비공개를 변경할 수 없습니다.");
        }

        jdbcTemplate.update(
                """
                UPDATE competitions
                SET is_public = ?, updated_at = SYSTIMESTAMP
                WHERE competition_id = ?
                """,
                isPublic ? 1 : 0,
                competitionId
        );
    }

    @Transactional
    public void deleteCompetition(Long competitionId) {
        Map<String, Object> competition = getCompetitionRow(competitionId);

        String storedStatus = (String) competition.get("STATUS");
        LocalDateTime startAt = toLocalDateTime((Timestamp) competition.get("START_AT"));
        LocalDateTime endAt = toLocalDateTime((Timestamp) competition.get("END_AT"));
        String displayStatus = resolveDisplayStatus(storedStatus, startAt, endAt);

        Integer participantCount = getJoinedParticipantCount(competitionId);

        if ("ONGOING".equalsIgnoreCase(displayStatus) && participantCount != null && participantCount > 0) {
            throw new RuntimeException("참가자가 있는 진행중 대회는 삭제할 수 없습니다.");
        }

        if ("ONGOING".equalsIgnoreCase(displayStatus) && (participantCount == null || participantCount == 0)) {
            jdbcTemplate.update(
                    """
                    UPDATE competitions
                    SET status = 'CANCELED',
                        is_public = 0,
                        updated_at = SYSTIMESTAMP
                    WHERE competition_id = ?
                    """,
                    competitionId
            );
            return;
        }

        if ("SCHEDULED".equalsIgnoreCase(displayStatus)) {
            jdbcTemplate.update(
                    """
                    UPDATE competitions
                    SET status = 'CANCELED',
                        is_public = 0,
                        updated_at = SYSTIMESTAMP
                    WHERE competition_id = ?
                    """,
                    competitionId
            );

            if (participantCount != null && participantCount > 0) {
                jdbcTemplate.update(
                        """
                        UPDATE competition_participants
                        SET participation_status = 'CANCELED'
                        WHERE competition_id = ?
                          AND participation_status = 'JOINED'
                        """,
                        competitionId
                );
            }
            return;
        }

        if ("ENDED".equalsIgnoreCase(displayStatus) || "CANCELED".equalsIgnoreCase(displayStatus)) {
            jdbcTemplate.update(
                    """
                    UPDATE competitions
                    SET is_public = 0,
                        updated_at = SYSTIMESTAMP
                    WHERE competition_id = ?
                    """,
                    competitionId
            );
            return;
        }

        throw new RuntimeException("삭제할 수 없는 대회입니다.");
    }

    private Map<String, Object> getCompetitionRow(Long competitionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT competition_id, start_at, end_at, status, is_public
                FROM competitions
                WHERE competition_id = ?
                """,
                competitionId
        );

        if (rows.isEmpty()) {
            throw new RuntimeException("존재하지 않는 대회입니다.");
        }

        return rows.get(0);
    }

    private Integer getJoinedParticipantCount(Long competitionId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM competition_participants
                WHERE competition_id = ?
                  AND participation_status = 'JOINED'
                """,
                Integer.class,
                competitionId
        );
    }

    private void validateSaveRequest(CompetitionSaveRequestDto request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new RuntimeException("대회명을 입력해주세요.");
        }

        if (request.getStartAt() == null || request.getEndAt() == null) {
            throw new RuntimeException("시작일과 종료일을 입력해주세요.");
        }

        if (!request.getStartAt().isBefore(request.getEndAt())) {
            throw new RuntimeException("종료일은 시작일보다 이후여야 합니다.");
        }

        if (request.getInitialSeedMoney() == null ||
                request.getInitialSeedMoney().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("초기 시드머니는 0보다 커야 합니다.");
        }

        if (request.getMaxParticipants() != null && request.getMaxParticipants() <= 0) {
            throw new RuntimeException("최대 참가자 수는 1 이상이어야 합니다.");
        }
    }

    private String resolveStatus(LocalDateTime startAt, LocalDateTime endAt) {
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(startAt)) {
            return "SCHEDULED";
        }

        if (now.isAfter(endAt)) {
            return "ENDED";
        }

        return "ONGOING";
    }

    private String resolveDisplayStatus(String storedStatus, LocalDateTime startAt, LocalDateTime endAt) {
        if ("CANCELED".equalsIgnoreCase(storedStatus)) {
            return "CANCELED";
        }
        return resolveStatus(startAt, endAt);
    }

    private boolean equalsInteger(Integer a, Integer b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    public List<CompetitionParticipantDto> getParticipants(Long competitionId) {
        String sql = """
            SELECT u.user_id,
                   u.email,
                   u.nickname,
                   cp.account_id,
                   cp.joined_at,
                   cp.participation_status
            FROM competition_participants cp
            JOIN users u ON cp.user_id = u.user_id
            WHERE cp.competition_id = ?
            ORDER BY cp.joined_at DESC
            """;

        return jdbcTemplate.query(
                sql,
                new Object[]{competitionId},
                (rs, rowNum) -> new CompetitionParticipantDto(
                        rs.getLong("user_id"),
                        rs.getString("email"),
                        rs.getString("nickname"),
                        rs.getLong("account_id"),
                        toLocalDateTime(rs.getTimestamp("joined_at")),
                        rs.getString("participation_status")
                )
        );
    }

    public List<CompetitionRankingResponseDto> getCompetitionRanking(Long competitionId) {
        String statusSql = """
                SELECT status, start_at, end_at, is_public
                FROM competitions
                WHERE competition_id = ?
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(statusSql, competitionId);

        if (rows.isEmpty()) {
            throw new RuntimeException("존재하지 않는 대회입니다.");
        }

        Map<String, Object> row = rows.get(0);
        String status = resolveDisplayStatus(
                (String) row.get("STATUS"),
                toLocalDateTime((Timestamp) row.get("START_AT")),
                toLocalDateTime((Timestamp) row.get("END_AT"))
        );

        if ("SCHEDULED".equalsIgnoreCase(status)) {
            throw new RuntimeException("예정된 대회는 랭킹을 조회할 수 없습니다.");
        }

        if ("CANCELED".equalsIgnoreCase(status)) {
            throw new RuntimeException("취소된 대회는 랭킹을 조회할 수 없습니다.");
        }

        String sql = """
        SELECT
            RANK() OVER (
                ORDER BY base.return_rate DESC,
                         base.total_asset DESC,
                         base.last_trade_at ASC
            ) AS ranking_rank,
            base.user_id,
            base.nickname,
            base.profile_image_url,
            base.total_asset,
            base.return_rate,
            base.profit_amount
        FROM (
            SELECT
                u.user_id,
                u.nickname,
                u.profile_image_url,
                (a.cash_balance + NVL(h_agg.total_stock_eval, 0)) AS total_asset,
                CASE
                    WHEN c.initial_seed_money = 0 THEN 0
                    ELSE ROUND(
                        ((a.cash_balance + NVL(h_agg.total_stock_eval, 0)) - c.initial_seed_money)
                        / c.initial_seed_money * 100,
                        2
                    )
                END AS return_rate,
                ((a.cash_balance + NVL(h_agg.total_stock_eval, 0)) - c.initial_seed_money) AS profit_amount,
                t_agg.last_trade_at
            FROM competition_participants cp
            JOIN users u ON cp.user_id = u.user_id
            JOIN accounts a ON cp.account_id = a.account_id
            JOIN competitions c ON cp.competition_id = c.competition_id
            JOIN (
                SELECT account_id,
                       MAX(ordered_at) AS last_trade_at
                FROM orders
                WHERE order_status = 'COMPLETED'
                GROUP BY account_id
            ) t_agg ON a.account_id = t_agg.account_id
            LEFT JOIN (
                SELECT h.account_id,
                       SUM(h.quantity * s.current_price) AS total_stock_eval
                FROM holdings h
                JOIN stock s ON h.stock_id = s.stock_id
                GROUP BY h.account_id
            ) h_agg ON a.account_id = h_agg.account_id
            WHERE cp.competition_id = ?
              AND cp.participation_status = 'JOINED'
        ) base
        ORDER BY base.return_rate DESC,
                 base.total_asset DESC,
                 base.last_trade_at ASC
        """;

        return jdbcTemplate.query(
                sql,
                new Object[]{competitionId},
                (rs, rowNum) -> new CompetitionRankingResponseDto(
                        rs.getInt("ranking_rank"),
                        rs.getLong("user_id"),
                        rs.getString("nickname"),
                        rs.getString("profile_image_url"),
                        rs.getBigDecimal("total_asset"),
                        rs.getBigDecimal("return_rate"),
                        rs.getBigDecimal("profit_amount")
                )
        );
    }
}