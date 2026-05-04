package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.CommunityAttachmentResponseDto;
import org.team12.teamproject.dto.CommunityCommentCreateRequestDto;
import org.team12.teamproject.dto.CommunityCommentResponseDto;
import org.team12.teamproject.dto.CommunityPostCreateRequestDto;
import org.team12.teamproject.dto.CommunityPostDetailResponseDto;
import org.team12.teamproject.dto.CommunityPostResponseDto;
import org.team12.teamproject.dto.CommunityPostUpdateRequestDto;
import org.team12.teamproject.dto.CommunityReportRequestDto;
import org.team12.teamproject.entity.Board;
import org.team12.teamproject.entity.Comment;
import org.team12.teamproject.entity.Post;
import org.team12.teamproject.entity.PostAttachment;
import org.team12.teamproject.entity.Report;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.entity.Vote;
import org.team12.teamproject.repository.BoardRepository;
import org.team12.teamproject.repository.CommentRepository;
import org.team12.teamproject.repository.OrderRepository;
import org.team12.teamproject.repository.PostAttachmentRepository;
import org.team12.teamproject.repository.PostRepository;
import org.team12.teamproject.repository.ReportRepository;
import org.team12.teamproject.repository.StockRepository;
import org.team12.teamproject.repository.UserRepository;
import org.team12.teamproject.repository.VoteRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommunityService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final VoteRepository voteRepository;
    private final ReportRepository reportRepository;
    private final PostAttachmentRepository postAttachmentRepository;
    private final UserActivityAuditLogger userActivityAuditLogger;
    private final AdminActionAuditLogger adminActionAuditLogger;

    private static final String STOCK_DISCUSSION_BOARD_CODE = "STOCK_DISCUSSION";
    private static final String FREE_BOARD_CODE = "FREE";
    private static final String NOTICE_BOARD_CODE = "NOTICE";

    private static final Set<String> ALLOWED_REPORT_REASONS = Set.of(
            "ABUSE",
            "SPAM",
            "ADVERTISEMENT",
            "SEXUAL",
            "ILLEGAL_FILMING",
            "ETC"
    );

    private static final DateTimeFormatter FILE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getStockPosts(String symbol) {
        Stock stock = stockRepository.findByStockCode(symbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다."));

        Board stockBoard = boardRepository.findByBoardCode(STOCK_DISCUSSION_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("종목 토론 게시판이 존재하지 않습니다."));

        List<Post> notices = postRepository.findByBoardIdAndStatusOrderByCreatedAtDesc(stockBoard.getId(), "NORMAL")
                .stream()
                .filter(Post::getIsNotice)
                .limit(3)
                .toList();

        List<Post> stockPosts = postRepository
                .findByStockIdAndStatusAndIsNoticeFalseOrderByCreatedAtDesc(stock.getId(), "NORMAL");

        List<Post> mergedPosts = new ArrayList<>();
        mergedPosts.addAll(notices);
        mergedPosts.addAll(stockPosts);

        return mergedPosts.stream()
                .map(this::toPostResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getNoticePosts() {
        Board noticeBoard = boardRepository.findByBoardCode(NOTICE_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("공지사항 게시판이 존재하지 않습니다."));

        return postRepository.findByBoardIdAndStatusOrderByCreatedAtDesc(noticeBoard.getId(), "NORMAL")
                .stream()
                .map(this::toPostResponseDto)
                .toList();
    }

    @Transactional
    public Long createNoticePost(CommunityPostCreateRequestDto request, String email, boolean isAdmin) {
        if (!isAdmin) {
            throw new IllegalArgumentException("관리자만 전역 공지를 작성할 수 있습니다.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Board board = boardRepository.findByBoardCode(NOTICE_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("공지사항 게시판이 존재하지 않습니다."));

        validatePostRequest(request.getTitle(), request.getContent());

        Post post = Post.builder()
                .board(board)
                .user(user)
                .stock(null)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .viewCount(0)
                .likeCount(0)
                .dislikeCount(0)
                .commentCount(0)
                .reportCount(0)
                .status("NORMAL")
                .isNotice(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Post savedPost = postRepository.save(post);

        finalizeTempAttachments(savedPost, user);
        syncAttachments(savedPost, user, request.getAttachmentIds(), false);

        userActivityAuditLogger.log(
                user.getId(),
                user.getEmail(),
                "POST_CREATE",
                "POST",
                String.valueOf(savedPost.getId()),
                "[GLOBAL_NOTICE] " + abbreviateForLog(savedPost.getTitle()) + " | " + abbreviateForLog(savedPost.getContent())
        );
        adminActionAuditLogger.log(
                user.getId(),
                user.getEmail(),
                "NOTICE_CREATE",
                "POST",
                String.valueOf(savedPost.getId()),
                "board=" + NOTICE_BOARD_CODE
                        + "; title=" + abbreviateForLog(savedPost.getTitle())
                        + "; content=" + abbreviateForLog(savedPost.getContent())
        );

        return savedPost.getId();
    }

    @Transactional
    public Long createStockPost(String symbol, CommunityPostCreateRequestDto request, String email, boolean isAdmin) {
        Stock stock = stockRepository.findByStockCode(symbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다."));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Board board = boardRepository.findByBoardCode(STOCK_DISCUSSION_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("종목 토론 게시판이 존재하지 않습니다."));

        validatePostRequest(request.getTitle(), request.getContent());

        boolean isNotice = isAdmin && Boolean.TRUE.equals(request.getIsNotice());

        Post post = Post.builder()
                .board(board)
                .user(user)
                .stock(isNotice ? null : stock)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .viewCount(0)
                .likeCount(0)
                .dislikeCount(0)
                .commentCount(0)
                .reportCount(0)
                .status("NORMAL")
                .isNotice(isNotice)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Post savedPost = postRepository.save(post);

        finalizeTempAttachments(savedPost, user);
        syncAttachments(savedPost, user, request.getAttachmentIds(), false);

        userActivityAuditLogger.log(
                user.getId(),
                user.getEmail(),
                "POST_CREATE",
                "POST",
                String.valueOf(savedPost.getId()),
                abbreviateForLog(savedPost.getTitle()) + " | " + abbreviateForLog(savedPost.getContent())
        );
        if (isNotice) {
            adminActionAuditLogger.log(
                    user.getId(),
                    user.getEmail(),
                    "NOTICE_CREATE",
                    "POST",
                    String.valueOf(savedPost.getId()),
                    "board=" + STOCK_DISCUSSION_BOARD_CODE
                            + "; title=" + abbreviateForLog(savedPost.getTitle())
                            + "; content=" + abbreviateForLog(savedPost.getContent())
            );
        }

        return savedPost.getId();
    }

    @Transactional(readOnly = true)
    public CommunityPostDetailResponseDto getPostDetail(Long postId, String email, boolean isAdmin) {
        Post post = (isAdmin ? postRepository.findById(postId) : postRepository.findByIdAndStatus(postId, "NORMAL"))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        Stock stock = post.getStock();

        List<CommunityCommentResponseDto> comments = (isAdmin
                ? commentRepository.findByPostIdOrderByCreatedAtAsc(postId)
                : commentRepository.findByPostIdAndStatusOrderByCreatedAtAsc(postId, "NORMAL"))
                .stream()
                .map(this::toCommentResponseDto)
                .toList();

        List<CommunityAttachmentResponseDto> attachments = postAttachmentRepository
                .findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(attachment -> CommunityAttachmentResponseDto.builder()
                        .attachmentId(attachment.getId())
                        .originalName(attachment.getOriginalName())
                        .fileUrl(attachment.getFileUrl())
                        .fileType(attachment.getFileType())
                        .contentType(attachment.getContentType())
                        .fileSize(attachment.getFileSize())
                        .build())
                .toList();

        boolean likedByCurrentUser = false;
        boolean votedByCurrentUser = false;
        String myVoteType = null;

        if (email != null) {
            User loginUser = userRepository.findByEmail(email).orElse(null);
            if (loginUser != null) {
                Vote myVote = voteRepository
                        .findByTargetTypeAndTargetIdAndUserId(
                                Vote.TARGET_TYPE_POST,
                                postId,
                                loginUser.getId()
                        )
                        .orElse(null);

                if (myVote != null) {
                    votedByCurrentUser = true;
                    myVoteType = myVote.getVoteType();
                    likedByCurrentUser = Vote.VOTE_TYPE_LIKE.equals(myVote.getVoteType());
                }
            }
        }

        return CommunityPostDetailResponseDto.builder()
                .postId(post.getId())
                .stockId(stock != null ? stock.getId() : null)
                .stockCode(stock != null ? stock.getStockCode() : null)
                .stockName(stock != null ? stock.getStockName() : null)
                .userId(post.getUser().getId())
                .nickname(post.getUser().getNickname())
                .level(calculateCommunityLevel(post.getUser().getId()))
                .levelIconUrl(getCommunityLevelIconUrl(calculateCommunityLevel(post.getUser().getId())))
                .hasBoughtStock(stock != null && hasBoughtStock(post.getUser().getId(), stock.getId()))
                .title(post.getTitle())
                .content(post.getContent())
                .commentCount(post.getCommentCount())
                .likeCount(post.getLikeCount())
                .dislikeCount(post.getDislikeCount())
                .viewCount(post.getViewCount())
                .status(post.getStatus())
                .deletedAt(post.getDeletedAt())
                .isNotice(post.getIsNotice())
                .createdAt(post.getCreatedAt())
                .likedByCurrentUser(likedByCurrentUser)
                .votedByCurrentUser(votedByCurrentUser)
                .myVoteType(myVoteType)
                .comments(comments)
                .attachments(attachments)
                .build();
    }

    @Transactional
    public void increaseViewCount(Long postId) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        post.increaseViewCount();
    }

    @Transactional
    public void likePost(Long postId, String email) {
        votePost(postId, email, Vote.VOTE_TYPE_LIKE);
    }

    @Transactional
    public void dislikePost(Long postId, String email) {
        votePost(postId, email, Vote.VOTE_TYPE_DISLIKE);
    }

    private void votePost(Long postId, String email, String voteType) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (post.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("본인 글은 추천/비추천할 수 없습니다.");
        }

        boolean alreadyVoted = voteRepository.existsByTargetTypeAndTargetIdAndUserId(
                Vote.TARGET_TYPE_POST,
                postId,
                user.getId()
        );

        if (alreadyVoted) {
            throw new IllegalArgumentException("이미 추천 또는 비추천한 게시글입니다.");
        }

        Vote vote = Vote.builder()
                .targetType(Vote.TARGET_TYPE_POST)
                .targetId(postId)
                .user(user)
                .voteType(voteType)
                .createdAt(LocalDateTime.now())
                .build();

        voteRepository.save(vote);

        if (Vote.VOTE_TYPE_LIKE.equals(voteType)) {
            post.increaseLikeCount();
        } else {
            post.increaseDislikeCount();
        }

        userActivityAuditLogger.log(
                user.getId(),
                user.getEmail(),
                Vote.VOTE_TYPE_LIKE.equals(voteType) ? "POST_LIKE" : "POST_DISLIKE",
                "POST",
                String.valueOf(post.getId()),
                abbreviateForLog(post.getTitle())
        );
    }

    @Transactional
    public Long createComment(Long postId, CommunityCommentCreateRequestDto request, String email) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }

        Comment parentComment = null;

        if (request.getParentCommentId() != null) {
            parentComment = commentRepository.findByIdAndStatus(request.getParentCommentId(), "NORMAL")
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부모 댓글입니다."));

            if (!parentComment.getPost().getId().equals(postId)) {
                throw new IllegalArgumentException("해당 게시글의 댓글에만 답글을 작성할 수 있습니다.");
            }

            if (parentComment.getParentComment() != null) {
                throw new IllegalArgumentException("대댓글에는 답글을 작성할 수 없습니다.");
            }
        }

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .parentComment(parentComment)
                .content(request.getContent().trim())
                .status("NORMAL")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        post.increaseCommentCount();
        Long commentId = commentRepository.save(comment).getId();

        userActivityAuditLogger.log(
                user.getId(),
                user.getEmail(),
                parentComment == null ? "COMMENT_CREATE" : "REPLY_CREATE",
                "COMMENT",
                String.valueOf(commentId),
                abbreviateForLog(comment.getContent())
        );

        return commentId;
    }

    @Transactional(readOnly = true)
    public List<CommunityCommentResponseDto> getComments(Long postId) {
        return commentRepository.findByPostIdAndStatusOrderByCreatedAtAsc(postId, "NORMAL")
                .stream()
                .map(this::toCommentResponseDto)
                .toList();
    }

    @Transactional
    public void updatePost(Long postId, CommunityPostUpdateRequestDto request, String email, boolean isAdmin) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        User loginUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        boolean isOwner = post.getUser().getId().equals(loginUser.getId());
        Long targetUserId = post.getUser().getId();
        String beforeTitle = post.getTitle();
        String beforeContent = post.getContent();
        Boolean beforeIsNotice = post.getIsNotice();

        if (!isAdmin && !isOwner) {
            throw new IllegalArgumentException("본인 게시글만 수정할 수 있습니다.");
        }

        validatePostRequest(request.getTitle(), request.getContent());

        boolean isNotice = post.getIsNotice();
        if (isAdmin) {
            isNotice = Boolean.TRUE.equals(request.getIsNotice());
        }

        post.updatePost(request.getTitle().trim(), request.getContent().trim(), isNotice);

        finalizeTempAttachments(post, loginUser);
        syncAttachments(post, loginUser, request.getAttachmentIds(), true);

        userActivityAuditLogger.log(
                loginUser.getId(),
                loginUser.getEmail(),
                "POST_UPDATE",
                "POST",
                String.valueOf(post.getId()),
                "targetUserId=" + targetUserId
                        + "; ownerAction=" + isOwner
                        + "; beforeIsNotice=" + beforeIsNotice
                        + "; afterIsNotice=" + post.getIsNotice()
                        + "; beforeTitle=" + abbreviateForLog(beforeTitle)
                        + "; beforeContent=" + abbreviateForLog(beforeContent)
                        + "; afterTitle=" + abbreviateForLog(post.getTitle())
                        + "; afterContent=" + abbreviateForLog(post.getContent())
        );
        if (isAdmin) {
            adminActionAuditLogger.log(
                    loginUser.getId(),
                    loginUser.getEmail(),
                    "POST_UPDATE",
                    "POST",
                    String.valueOf(post.getId()),
                    "targetUserId=" + targetUserId
                            + "; ownerAction=" + isOwner
                            + "; beforeIsNotice=" + beforeIsNotice
                            + "; afterIsNotice=" + post.getIsNotice()
                            + "; beforeTitle=" + abbreviateForLog(beforeTitle)
                            + "; afterTitle=" + abbreviateForLog(post.getTitle())
                            + "; beforeContent=" + abbreviateForLog(beforeContent)
                            + "; afterContent=" + abbreviateForLog(post.getContent())
            );
        }
    }

    @Transactional
    public void deletePost(Long postId, String email, boolean isAdmin) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        User loginUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        boolean isOwner = post.getUser().getId().equals(loginUser.getId());

        if (!isAdmin && !isOwner) {
            throw new IllegalArgumentException("본인 게시글만 삭제할 수 있습니다.");
        }

        userActivityAuditLogger.log(
                loginUser.getId(),
                loginUser.getEmail(),
                "POST_DELETE",
                "POST",
                String.valueOf(post.getId()),
                "targetUserId=" + post.getUser().getId()
                        + "; ownerAction=" + isOwner
                        + "; isNotice=" + post.getIsNotice()
                        + "; title=" + abbreviateForLog(post.getTitle())
                        + "; content=" + abbreviateForLog(post.getContent())
        );
        if (isAdmin) {
            adminActionAuditLogger.log(
                    loginUser.getId(),
                    loginUser.getEmail(),
                    "POST_DELETE",
                    "POST",
                    String.valueOf(post.getId()),
                    "targetUserId=" + post.getUser().getId()
                            + "; ownerAction=" + isOwner
                            + "; isNotice=" + post.getIsNotice()
                            + "; title=" + abbreviateForLog(post.getTitle())
                            + "; content=" + abbreviateForLog(post.getContent())
            );
        }

        post.softDelete();
    }

    @Transactional
    public void deleteComment(Long commentId, String email, boolean isAdmin) {
        Comment comment = commentRepository.findByIdAndStatus(commentId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다."));

        User loginUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        boolean isOwner = comment.getUser().getId().equals(loginUser.getId());

        if (!isAdmin && !isOwner) {
            throw new IllegalArgumentException("본인 댓글만 삭제할 수 있습니다.");
        }

        Post post = comment.getPost();

        userActivityAuditLogger.log(
                loginUser.getId(),
                loginUser.getEmail(),
                "COMMENT_DELETE",
                "COMMENT",
                String.valueOf(comment.getId()),
                "targetUserId=" + comment.getUser().getId()
                        + "; ownerAction=" + isOwner
                        + "; postId=" + post.getId()
                        + "; content=" + abbreviateForLog(comment.getContent())
        );
        if (isAdmin) {
            adminActionAuditLogger.log(
                    loginUser.getId(),
                    loginUser.getEmail(),
                    "COMMENT_DELETE",
                    "COMMENT",
                    String.valueOf(comment.getId()),
                    "targetUserId=" + comment.getUser().getId()
                            + "; ownerAction=" + isOwner
                            + "; postId=" + post.getId()
                            + "; content=" + abbreviateForLog(comment.getContent())
            );
        }

        comment.softDelete();
        post.decreaseCommentCount();
    }

    @Transactional
    public void reportPost(Long postId, CommunityReportRequestDto request, String email) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        User reporter = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        validateReportReason(request);

        if (post.getUser().getId().equals(reporter.getId())) {
            throw new IllegalArgumentException("본인 게시글은 신고할 수 없습니다.");
        }

        boolean alreadyReported = reportRepository.existsByTargetTypeAndTargetIdAndReporterUserId(
                Report.TARGET_TYPE_POST,
                postId,
                reporter.getId()
        );

        if (alreadyReported) {
            throw new IllegalArgumentException("이미 신고한 게시글입니다.");
        }

        Report report = Report.builder()
                .targetType(Report.TARGET_TYPE_POST)
                .targetId(postId)
                .reporterUser(reporter)
                .reason(request.getReason().trim())
                .detail(normalizeDetail(request.getDetail()))
                .reportStatus("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        reportRepository.save(report);
        post.increaseReportCount();

        userActivityAuditLogger.log(
                reporter.getId(),
                reporter.getEmail(),
                "REPORT_CREATE",
                "POST",
                String.valueOf(postId),
                "reason=" + request.getReason().trim()
        );
    }

    @Transactional
    public void reportComment(Long commentId, CommunityReportRequestDto request, String email) {
        Comment comment = commentRepository.findByIdAndStatus(commentId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다."));

        User reporter = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        validateReportReason(request);

        if (comment.getUser().getId().equals(reporter.getId())) {
            throw new IllegalArgumentException("본인 댓글은 신고할 수 없습니다.");
        }

        boolean alreadyReported = reportRepository.existsByTargetTypeAndTargetIdAndReporterUserId(
                Report.TARGET_TYPE_COMMENT,
                commentId,
                reporter.getId()
        );

        if (alreadyReported) {
            throw new IllegalArgumentException("이미 신고한 댓글입니다.");
        }

        Report report = Report.builder()
                .targetType(Report.TARGET_TYPE_COMMENT)
                .targetId(commentId)
                .reporterUser(reporter)
                .reason(request.getReason().trim())
                .detail(normalizeDetail(request.getDetail()))
                .reportStatus("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        reportRepository.save(report);

        userActivityAuditLogger.log(
                reporter.getId(),
                reporter.getEmail(),
                "REPORT_CREATE",
                "COMMENT",
                String.valueOf(commentId),
                "reason=" + request.getReason().trim()
        );
    }

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getFreeCommentedPosts(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Board board = boardRepository.findByBoardCode(FREE_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("자유게시판이 존재하지 않습니다."));

        List<Comment> comments = commentRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());

        return comments.stream()
                .map(Comment::getPost)
                .filter(post -> post != null)
                .filter(post -> "NORMAL".equals(post.getStatus()))
                .filter(post -> post.getBoard() != null && board.getId().equals(post.getBoard().getId()))
                .distinct()
                .map(this::toPostResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getStockCommentedPosts(String symbol, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Stock stock = stockRepository.findByStockCode(symbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다."));

        Board board = boardRepository.findByBoardCode(STOCK_DISCUSSION_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("종목 토론 게시판이 존재하지 않습니다."));

        List<Comment> comments = commentRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());

        return comments.stream()
                .map(Comment::getPost)
                .filter(post -> post != null)
                .filter(post -> "NORMAL".equals(post.getStatus()))
                .filter(post -> post.getBoard() != null && board.getId().equals(post.getBoard().getId()))
                .filter(post -> post.getStock() != null && stock.getId().equals(post.getStock().getId()))
                .distinct()
                .map(this::toPostResponseDto)
                .toList();
    }


    private CommunityPostResponseDto toPostResponseDto(Post post) {
        User user = post.getUser();
        Stock stock = post.getStock();
        int communityLevel = calculateCommunityLevel(user.getId());

        return CommunityPostResponseDto.builder()
                .postId(post.getId())
                .stockId(stock != null ? stock.getId() : null)
                .stockCode(stock != null ? stock.getStockCode() : null)
                .stockName(stock != null ? stock.getStockName() : null)
                .userId(user.getId())
                .nickname(user.getNickname())
                .level(communityLevel)
                .levelIconUrl(getCommunityLevelIconUrl(communityLevel))
                .hasBoughtStock(stock != null && hasBoughtStock(user.getId(), stock.getId()))
                .title(post.getTitle())
                .commentCount(post.getCommentCount())
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .dislikeCount(post.getDislikeCount())
                .isNotice(post.getIsNotice())
                .createdAt(post.getCreatedAt())
                .build();
    }

    private int calculateCommunityLevel(Long userId) {
        long postCount = postRepository.countByUser_IdAndStatus(userId, "NORMAL");
        long commentCount = commentRepository.countByUser_IdAndStatus(userId, "NORMAL");
        long receivedLikeCount = postRepository.sumLikeCountByUserIdAndStatus(userId, "NORMAL");

        int activityScore = (int) ((postCount * 5) + (commentCount * 2) + (receivedLikeCount * 10));

        if (activityScore >= 1800) return 10;
        if (activityScore >= 1200) return 9;
        if (activityScore >= 800) return 8;
        if (activityScore >= 500) return 7;
        if (activityScore >= 300) return 6;
        if (activityScore >= 180) return 5;
        if (activityScore >= 100) return 4;
        if (activityScore >= 50) return 3;
        if (activityScore >= 20) return 2;
        return 1;
    }

    private String getCommunityLevelIconUrl(int level) {
        return "/assets/community-badges/level-" + level + ".png";
    }

    private CommunityCommentResponseDto toCommentResponseDto(Comment comment) {
        return CommunityCommentResponseDto.builder()
                .commentId(comment.getId())
                .parentCommentId(
                        comment.getParentComment() != null
                                ? comment.getParentComment().getId()
                                : null
                )
                .userId(comment.getUser().getId())
                .nickname(comment.getUser().getNickname())
                .level(calculateCommunityLevel(comment.getUser().getId()))
                .levelIconUrl(getCommunityLevelIconUrl(calculateCommunityLevel(comment.getUser().getId())))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private void validateReportReason(CommunityReportRequestDto request) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("신고 사유를 선택해주세요.");
        }

        String reason = request.getReason().trim();
        if (!ALLOWED_REPORT_REASONS.contains(reason)) {
            throw new IllegalArgumentException("올바르지 않은 신고 사유입니다.");
        }
    }

    private String normalizeDetail(String detail) {
        if (detail == null || detail.trim().isEmpty()) {
            return null;
        }
        return detail.trim();
    }

    private void validatePostRequest(String title, String content) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }

        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
    }

    private void syncAttachments(Post post, User user, List<Long> attachmentIds, boolean isUpdate) {
        List<Long> normalizedIds = attachmentIds == null
                ? List.of()
                : attachmentIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (isUpdate) {
            List<PostAttachment> existingAttachments = postAttachmentRepository.findByPostIdOrderByCreatedAtAsc(post.getId());
            Set<Long> keepIds = new LinkedHashSet<>(normalizedIds);

            for (PostAttachment existing : existingAttachments) {
                if (!keepIds.contains(existing.getId())) {
                    existing.detachFromPost();
                }
            }
        }

        if (normalizedIds.isEmpty()) {
            return;
        }

        List<PostAttachment> attachments = postAttachmentRepository.findByIdIn(normalizedIds);

        if (attachments.size() != normalizedIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 첨부파일이 포함되어 있습니다.");
        }

        for (PostAttachment attachment : attachments) {
            if (!attachment.isOwner(user.getId())) {
                throw new IllegalArgumentException("본인이 업로드한 파일만 첨부할 수 있습니다.");
            }

            if (attachment.getPost() != null && !attachment.getPost().getId().equals(post.getId())) {
                throw new IllegalArgumentException("이미 다른 게시글에 연결된 첨부파일입니다.");
            }

            attachment.assignToPost(post);
        }
    }

    private void finalizeTempAttachments(Post post, User user) {
        List<PostAttachment> tempFiles = postAttachmentRepository
                .findTempFilesByUser(user.getId())
                .stream()
                .filter(file -> "FILE".equals(file.getFileType()))
                .toList();

        if (tempFiles.isEmpty()) {
            return;
        }

        int count = postAttachmentRepository.countByPostId(post.getId());

        for (PostAttachment file : tempFiles) {
            count++;

            String extension = extractExtension(file.getStoredName());
            String newName = createFinalFileName(user.getId(), post.getId(), count, extension);

            Path rootPath = getUploadRootPath();
            String subDir = extractSubDir(file.getFileUrl());

            Path oldPath = rootPath.resolve(subDir).resolve(file.getStoredName()).normalize();
            Path newPath = oldPath.getParent().resolve(newName).normalize();

            try {
                System.out.println("=== finalizeTempAttachments start ===");
                System.out.println("oldPath = " + oldPath);
                System.out.println("newPath = " + newPath);
                System.out.println("oldPath exists = " + Files.exists(oldPath));
                System.out.println("newPath exists = " + Files.exists(newPath));

                if (!Files.exists(oldPath)) {
                    throw new IllegalArgumentException("임시 파일을 찾을 수 없습니다: " + oldPath);
                }

                Files.createDirectories(newPath.getParent());
                Files.copy(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);

                try {
                    Files.deleteIfExists(oldPath);
                } catch (IOException deleteEx) {
                    deleteEx.printStackTrace();
                }

                String newUrl = "/api/uploads/" + subDir.replace("\\", "/") + "/" + newName;
                file.updateFileInfo(newName, newUrl, post);

                System.out.println("=== finalizeTempAttachments success ===");
            } catch (Exception e) {
                e.printStackTrace();
                throw new IllegalArgumentException(
                        "파일 최종저장중 오류가 발생했습니다. oldPath=" + oldPath
                                + ", newPath=" + newPath
                                + ", reason=" + e.getMessage()
                );
            }
        }
    }

    private String createFinalFileName(Long userId, Long postId, int count, String extension) {
        String date = LocalDate.now().format(FILE_DATE_FORMATTER);
        return date + "_" + userId + "_" + postId + "." + count + extension;
    }

    private String extractSubDir(String fileUrl) {
        String path = fileUrl.replace("/api/uploads/", "").replace("/uploads/", "");
        int lastSlashIndex = path.lastIndexOf("/");
        if (lastSlashIndex < 0) {
            throw new IllegalArgumentException("잘못된 파일 경로입니다.");
        }
        return path.substring(0, lastSlashIndex);
    }

    private String extractExtension(String name) {
        int idx = name.lastIndexOf(".");
        return idx < 0 ? "" : name.substring(idx);
    }

    private Path getUploadRootPath() {
        Path configuredPath = Paths.get(uploadDir);

        if (configuredPath.isAbsolute()) {
            return configuredPath;
        }

        return Paths.get(System.getProperty("user.dir"), uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    private boolean hasBoughtStock(Long userId, Long stockId) {
        return orderRepository
                .existsByAccountUserIdAndStockIdAndOrderSideIgnoreCaseAndOrderStatusIgnoreCase(
                        userId,
                        stockId,
                        "BUY",
                        "COMPLETED"
                ) == 1;
    }

    private String abbreviateForLog(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        String normalized = value.replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replace(";", ",")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.length() <= 500) {
            return normalized;
        }

        return normalized.substring(0, 500) + "...";
    }

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getFreePosts() {
        Board board = boardRepository.findByBoardCode(FREE_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("자유게시판이 존재하지 않습니다."));

        return postRepository.findByBoardIdAndStatusOrderByCreatedAtDesc(board.getId(), "NORMAL")
                .stream()
                .map(this::toPostResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getFreeNoticePosts() {
        Board board = boardRepository.findByBoardCode(FREE_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("자유게시판이 존재하지 않습니다."));

        return postRepository.findByBoardIdAndStatusOrderByCreatedAtDesc(board.getId(), "NORMAL")
                .stream()
                .filter(Post::getIsNotice)
                .map(this::toPostResponseDto)
                .toList();
    }

    @Transactional
    public Long createFreePost(CommunityPostCreateRequestDto request, String email, boolean isAdmin) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Board board = boardRepository.findByBoardCode(FREE_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("자유게시판이 존재하지 않습니다."));

        validatePostRequest(request.getTitle(), request.getContent());

        boolean isNotice = isAdmin && Boolean.TRUE.equals(request.getIsNotice());

        Post post = Post.builder()
                .board(board)
                .user(user)
                .stock(null)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .viewCount(0)
                .likeCount(0)
                .dislikeCount(0)
                .commentCount(0)
                .reportCount(0)
                .status("NORMAL")
                .isNotice(isNotice)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Post savedPost = postRepository.save(post);

        finalizeTempAttachments(savedPost, user);
        syncAttachments(savedPost, user, request.getAttachmentIds(), false);

        userActivityAuditLogger.log(
                user.getId(),
                user.getEmail(),
                "POST_CREATE",
                "POST",
                String.valueOf(savedPost.getId()),
                abbreviateForLog(savedPost.getTitle()) + " | " + abbreviateForLog(savedPost.getContent())
        );
        if (isNotice) {
            adminActionAuditLogger.log(
                    user.getId(),
                    user.getEmail(),
                    "NOTICE_CREATE",
                    "POST",
                    String.valueOf(savedPost.getId()),
                    "board=" + FREE_BOARD_CODE
                            + "; title=" + abbreviateForLog(savedPost.getTitle())
                            + "; content=" + abbreviateForLog(savedPost.getContent())
            );
        }

        return savedPost.getId();
    }
}