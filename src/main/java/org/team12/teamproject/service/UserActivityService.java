package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.AdminActionLogItemDto;
import org.team12.teamproject.dto.AdminLoginLogItemDto;
import org.team12.teamproject.dto.UserActivityItemDto;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.CommentRepository;
import org.team12.teamproject.repository.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserActivityService {

    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) " +
                    "userId=(?<userId>.*?), userEmail=(?<userEmail>.*?), action=(?<action>.*?), " +
                    "targetType=(?<targetType>.*?), targetId=(?<targetId>.*?), detail=(?<detail>.*)$"
    );

    private static final Pattern ADMIN_LOG_LINE_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) " +
                    "adminUserId=(?<adminUserId>.*?), adminEmail=(?<adminEmail>.*?), action=(?<action>.*?), " +
                    "targetType=(?<targetType>.*?), targetId=(?<targetId>.*?), detail=(?<detail>.*)$"
    );

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserActivityItemDto> getUserActivities(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        log.info("사용자 활동 로그 조회 시작: userId={}", userId);

        List<UserActivityItemDto> activities = readLogFiles()
                .map(this::parseLine)
                .flatMap(java.util.Optional::stream)
                .filter(item -> String.valueOf(userId).equals(item.userId()))
                .filter(item -> !item.action().startsWith("NOTIFICATION"))
                .sorted(Comparator.comparing(ParsedActivity::occurredAt).reversed())
                .map(item -> toDto(item, user))
                .toList();

        log.info("사용자 활동 로그 조회 완료: userId={}, activityCount={}", userId, activities.size());
        return activities;
    }

    @Transactional(readOnly = true)
    public List<AdminLoginLogItemDto> getLoginLogs() {
        Map<Long, User> userMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return readLogFiles()
                .map(this::parseLine)
                .flatMap(java.util.Optional::stream)
                .filter(item -> "LOGIN".equals(item.action()) || "LOGOUT".equals(item.action()))
                .sorted(Comparator.comparing(ParsedActivity::occurredAt).reversed())
                .map(item -> toLoginLogDto(item, userMap))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminActionLogItemDto> getAdminActionLogs() {
        Map<Long, User> userMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return readAdminLogFiles()
                .map(this::parseAdminLine)
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(ParsedAdminAction::occurredAt).reversed())
                .map(item -> toAdminActionLogDto(item, userMap))
                .toList();
    }

    private Stream<String> readLogFiles() {
        Path logDir = Paths.get("logs");
        if (!Files.exists(logDir)) {
            return Stream.empty();
        }

        try {
            return Files.list(logDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("user-activity"))
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .flatMap(this::readLinesSafely);
        } catch (IOException e) {
            log.warn("사용자 활동 로그 디렉터리 조회 실패: {}", e.getMessage());
            return Stream.empty();
        }
    }

    private Stream<String> readAdminLogFiles() {
        Path logDir = Paths.get("logs");
        if (!Files.exists(logDir)) {
            return Stream.empty();
        }

        try {
            return Files.list(logDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("admin-action"))
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .flatMap(this::readLinesSafely);
        } catch (IOException e) {
            log.warn("관리자 행동 로그 디렉터리 조회 실패: {}", e.getMessage());
            return Stream.empty();
        }
    }

    private Stream<String> readLinesSafely(Path path) {
        try {
            return Files.lines(path);
        } catch (IOException e) {
            log.warn("사용자 활동 로그 파일 읽기 실패: path={}, reason={}", path, e.getMessage());
            return Stream.empty();
        }
    }

    private java.util.Optional<ParsedActivity> parseLine(String line) {
        Matcher matcher = LOG_LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of(new ParsedActivity(
                    matcher.group("userId").trim(),
                    matcher.group("userEmail").trim(),
                    matcher.group("action").trim(),
                    matcher.group("targetType").trim(),
                    matcher.group("targetId").trim(),
                    matcher.group("detail").trim(),
                    LocalDateTime.parse(matcher.group("timestamp"), LOG_TIMESTAMP_FORMAT)
            ));
        } catch (DateTimeParseException e) {
            log.warn("사용자 활동 로그 시간 파싱 실패: {}", line);
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<ParsedAdminAction> parseAdminLine(String line) {
        Matcher matcher = ADMIN_LOG_LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of(new ParsedAdminAction(
                    matcher.group("adminUserId").trim(),
                    matcher.group("adminEmail").trim(),
                    matcher.group("action").trim(),
                    matcher.group("targetType").trim(),
                    matcher.group("targetId").trim(),
                    matcher.group("detail").trim(),
                    LocalDateTime.parse(matcher.group("timestamp"), LOG_TIMESTAMP_FORMAT)
            ));
        } catch (DateTimeParseException e) {
            log.warn("관리자 행동 로그 시간 파싱 실패: {}", line);
            return java.util.Optional.empty();
        }
    }

    private UserActivityItemDto toDto(ParsedActivity activity, User user) {
        return UserActivityItemDto.builder()
                .actionType(activity.action())
                .actionLabel(toActionLabel(activity.action()))
                .targetType(activity.targetType())
                .targetId(activity.targetId())
                .postId(resolvePostId(activity))
                .targetLabel(toTargetLabel(activity.targetType(), activity.targetId()))
                .detail(toDetailLabel(activity.action(), activity.detail(), user))
                .occurredAt(activity.occurredAt().toString())
                .build();
    }

    private AdminLoginLogItemDto toLoginLogDto(ParsedActivity activity, Map<Long, User> userMap) {
        User user = null;
        try {
            user = userMap.get(Long.parseLong(activity.userId()));
        } catch (NumberFormatException ignored) {
        }

        return AdminLoginLogItemDto.builder()
                .occurredAt(activity.occurredAt().toString())
                .nickname(user != null ? user.getNickname() : "-")
                .loginId(activity.userEmail())
                .actionLabel("LOGIN".equals(activity.action()) ? "로그인" : "로그아웃")
                .build();
    }

    private AdminActionLogItemDto toAdminActionLogDto(ParsedAdminAction action, Map<Long, User> userMap) {
        User admin = null;
        try {
            admin = userMap.get(Long.parseLong(action.adminUserId()));
        } catch (NumberFormatException ignored) {
        }

        return AdminActionLogItemDto.builder()
                .occurredAt(action.occurredAt().toString())
                .adminUserId(action.adminUserId())
                .adminEmail(action.adminEmail())
                .adminNickname(admin != null ? admin.getNickname() : "-")
                .actionType(action.action())
                .actionLabel(toAdminActionLabel(action.action()))
                .targetType(action.targetType())
                .targetId(action.targetId())
                .targetLabel(toTargetLabel(action.targetType(), action.targetId()))
                .detail(action.detail())
                .build();
    }

    private Long resolvePostId(ParsedActivity activity) {
        if ("POST".equals(activity.targetType())) {
            try {
                return Long.parseLong(activity.targetId());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if ("COMMENT".equals(activity.targetType())) {
            try {
                Long commentId = Long.parseLong(activity.targetId());
                return commentRepository.findById(commentId)
                        .map(comment -> comment.getPost() != null ? comment.getPost().getId() : null)
                        .orElse(null);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    private String toActionLabel(String action) {
        return switch (action) {
            case "LOGIN" -> "로그인";
            case "LOGOUT" -> "로그아웃";
            case "POST_CREATE" -> "게시글 작성";
            case "POST_UPDATE" -> "게시글 수정";
            case "POST_DELETE" -> "게시글 삭제";
            case "COMMENT_CREATE" -> "댓글 작성";
            case "COMMENT_DELETE" -> "댓글 삭제";
            case "POST_LIKE" -> "게시글 추천";
            case "ORDER_BUY" -> "주식 매수";
            case "ORDER_SELL" -> "주식 매도";
            case "PROFILE_NICKNAME_UPDATE" -> "닉네임 변경";
            case "ACCOUNT_RESET" -> "기본 계좌 초기화";
            case "INQUIRY_CREATE" -> "문의 작성";
            case "INQUIRY_REPLY" -> "문의 답변";
            case "INQUIRY_READ" -> "문의 확인";
            case "REPORT_CREATE" -> "신고 접수";
            case "SUSPENSION_SET" -> "계정 정지";
            case "SUSPENSION_RELEASE" -> "정지 해제";
            default -> action;
        };
    }

    private String toAdminActionLabel(String action) {
        return switch (action) {
            case "NOTICE_CREATE" -> "공지 작성";
            case "INQUIRY_REPLY" -> "문의 답변";
            case "POST_UPDATE" -> "게시글 수정";
            case "POST_DELETE" -> "게시글 삭제";
            case "COMMENT_DELETE" -> "댓글 삭제";
            case "USER_UPDATE" -> "회원 정보 변경";
            default -> action;
        };
    }

    private String toTargetLabel(String targetType, String targetId) {
        String normalizedType = switch (targetType) {
            case "USER" -> "사용자";
            case "INQUIRY" -> "문의";
            case "POST" -> "게시글";
            case "COMMENT" -> "댓글";
            case "ORDER" -> "주문";
            case "ACCOUNT" -> "계좌";
            default -> targetType;
        };

        if (targetId == null || targetId.isBlank() || "-".equals(targetId)) {
            return normalizedType;
        }

        return normalizedType + " #" + targetId;
    }

    private String toDetailLabel(String action, String detail, User user) {
        if (detail == null || detail.isBlank() || "-".equals(detail)) {
            return "-";
        }

        if ("INQUIRY_CREATE".equals(action)) {
            String[] parts = detail.split(":", 2);
            if (parts.length == 2) {
                return "카테고리 " + parts[0] + " / 제목 " + parts[1];
            }
        }

        if ("POST_CREATE".equals(action) || "POST_UPDATE".equals(action) || "POST_DELETE".equals(action)) {
            return detail;
        }

        if ("COMMENT_CREATE".equals(action) || "COMMENT_DELETE".equals(action)) {
            return detail;
        }

        if ("POST_LIKE".equals(action)) {
            return "게시글 추천 " + detail;
        }

        if ("ORDER_BUY".equals(action) || "ORDER_SELL".equals(action)) {
            return toOrderDetailLabel(action, detail);
        }

        if ("PROFILE_NICKNAME_UPDATE".equals(action)) {
            return toNicknameUpdateDetailLabel(detail);
        }

        if ("ACCOUNT_RESET".equals(action)) {
            return toAccountResetDetailLabel(detail);
        }

        if ("INQUIRY_REPLY".equals(action) && detail.startsWith("answered_by_admin=")) {
            return "관리자 답변 등록";
        }

        if ("REPORT_CREATE".equals(action) && detail.startsWith("reason=")) {
            return "사유 " + detail.substring("reason=".length());
        }

        if ("SUSPENSION_SET".equals(action)) {
            return toSuspensionSetDetailLabel(detail);
        }

        if ("SUSPENSION_RELEASE".equals(action)) {
            return toSuspensionReleaseDetailLabel(detail);
        }

        if ("LOGIN".equals(action) && detail.startsWith("role=")) {
            return user.getNickname() + " 계정 로그인";
        }

        if ("LOGOUT".equals(action)) {
            return "클라이언트 로그아웃";
        }

        if ("INQUIRY_READ".equals(action)) {
            return "문의 답변 확인";
        }

        return detail;
    }

    private String toOrderDetailLabel(String action, String detail) {
        Map<String, String> values = Stream.of(detail.split(", "))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> left));

        String stockName = values.getOrDefault("stockName", "-");
        String stockCode = values.getOrDefault("stockCode", "-");
        String quantity = values.getOrDefault("quantity", "-");
        String price = values.getOrDefault("price", "-");
        String totalAmount = values.getOrDefault("totalAmount", "-");
        String side = "ORDER_BUY".equals(action) ? "매수" : "매도";

        return String.format(
                "%s(%s) %s주 %s 체결 / 체결가 %s원 / 총액 %s원",
                stockName,
                stockCode,
                quantity,
                side,
                price,
                totalAmount
        );
    }

    private String toNicknameUpdateDetailLabel(String detail) {
        Map<String, String> values = Stream.of(detail.split(", "))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> left));

        String before = values.getOrDefault("before", "-");
        String after = values.getOrDefault("after", "-");
        return String.format("닉네임 변경: %s -> %s", before, after);
    }

    private String toAccountResetDetailLabel(String detail) {
        Map<String, String> values = Stream.of(detail.split(", "))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> left));

        String accountName = values.getOrDefault("accountName", "기본 계좌");
        String cashBalance = values.getOrDefault("cashBalance", "5000000");
        return String.format("%s 초기화 / 예수금 %s원으로 재설정", accountName, cashBalance);
    }

    private String toSuspensionSetDetailLabel(String detail) {
        Map<String, String> values = Stream.of(detail.split("; "))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> left));

        String hours = values.getOrDefault("hours", "-");
        String until = values.getOrDefault("until", "-");
        String reason = values.getOrDefault("reason", "-");
        String period = "PERMANENT".equals(until)
                ? "영구 정지"
                : hours + "시간 정지 / 해제 예정 " + until;
        return period + " / 사유 " + reason;
    }

    private String toSuspensionReleaseDetailLabel(String detail) {
        Map<String, String> values = Stream.of(detail.split("; "))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> left));

        String type = values.getOrDefault("type", "-");
        if ("AUTO".equals(type)) {
            return "정지 기간 만료로 자동 해제";
        }
        String nextStatus = values.getOrDefault("nextStatus", "ACTIVE");
        return "관리자 수동 해제 / 변경 상태 " + nextStatus;
    }

    private record ParsedActivity(
            String userId,
            String userEmail,
            String action,
            String targetType,
            String targetId,
            String detail,
            LocalDateTime occurredAt
    ) {
    }

    private record ParsedAdminAction(
            String adminUserId,
            String adminEmail,
            String action,
            String targetType,
            String targetId,
            String detail,
            LocalDateTime occurredAt
    ) {
    }
}
