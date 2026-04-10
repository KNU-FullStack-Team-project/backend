package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.CommunityCommentCreateRequestDto;
import org.team12.teamproject.dto.CommunityCommentResponseDto;
import org.team12.teamproject.dto.CommunityPostCreateRequestDto;
import org.team12.teamproject.dto.CommunityPostDetailResponseDto;
import org.team12.teamproject.dto.CommunityPostResponseDto;
import org.team12.teamproject.dto.CommunityPostUpdateRequestDto;
import org.team12.teamproject.dto.CommunityReportRequestDto;
import org.team12.teamproject.entity.Board;
import org.team12.teamproject.entity.Comment;
import org.team12.teamproject.entity.CommentReport;
import org.team12.teamproject.entity.Post;
import org.team12.teamproject.entity.PostLike;
import org.team12.teamproject.entity.PostReport;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.BoardRepository;
import org.team12.teamproject.repository.CommentReportRepository;
import org.team12.teamproject.repository.CommentRepository;
import org.team12.teamproject.repository.OrderRepository;
import org.team12.teamproject.repository.PostLikeRepository;
import org.team12.teamproject.repository.PostReportRepository;
import org.team12.teamproject.repository.PostRepository;
import org.team12.teamproject.repository.StockRepository;
import org.team12.teamproject.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostReportRepository postReportRepository;
    private final CommentReportRepository commentReportRepository;

    private static final String STOCK_DISCUSSION_BOARD_CODE = "STOCK_DISCUSSION";

    private static final Set<String> ALLOWED_REPORT_REASONS = Set.of(
            "ABUSE",
            "SPAM",
            "ADVERTISEMENT",
            "SEXUAL",
            "ILLEGAL_FILMING",
            "ETC"
    );

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getStockPosts(String symbol) {
        System.out.println("=== service getStockPosts start: " + symbol);
        Stock stock = stockRepository.findByStockCode(symbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다."));

        List<Post> notices =
                postRepository.findByIsNoticeTrueAndStatusOrderByCreatedAtDesc("NORMAL");

        if (notices.size() > 3) {
            notices = notices.subList(0, 3);
        }

        List<Post> stockPosts = postRepository
                .findByStockIdAndStatusAndIsNoticeFalseOrderByCreatedAtDesc(stock.getId(), "NORMAL");

        System.out.println("=== notices size = " + notices.size());
        System.out.println("=== stockPosts size = " + stockPosts.size());

        List<Post> mergedPosts = new ArrayList<>();
        mergedPosts.addAll(notices);
        mergedPosts.addAll(stockPosts);

        return mergedPosts.stream()
                .map(post -> CommunityPostResponseDto.builder()
                        .postId(post.getId())
                        .stockId(post.getStock() != null ? post.getStock().getId() : null)
                        .stockCode(post.getStock() != null ? post.getStock().getStockCode() : null)
                        .stockName(post.getStock() != null ? post.getStock().getStockName() : null)
                        .userId(post.getUser().getId())
                        .nickname(post.getUser().getNickname())
                        .hasBoughtStock(
                                post.getStock() != null &&
                                hasBoughtStock(post.getUser().getId(), post.getStock().getId())
                        )
                        .title(post.getTitle())
                        .commentCount(post.getCommentCount())
                        .viewCount(post.getViewCount())
                        .likeCount(post.getLikeCount())
                        .isNotice(post.getIsNotice())
                        .createdAt(post.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getNoticePosts() {
        return postRepository.findByIsNoticeTrueAndStatusOrderByCreatedAtDesc("NORMAL")
                .stream()
                .map(post -> CommunityPostResponseDto.builder()
                        .postId(post.getId())
                        .stockId(null)
                        .stockCode(null)
                        .stockName(null)
                        .userId(post.getUser().getId())
                        .nickname(post.getUser().getNickname())
                        .hasBoughtStock(false)
                        .title(post.getTitle())
                        .commentCount(post.getCommentCount())
                        .viewCount(post.getViewCount())
                        .likeCount(post.getLikeCount())
                        .isNotice(post.getIsNotice())
                        .createdAt(post.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public Long createStockPost(String symbol, CommunityPostCreateRequestDto request, String email, boolean isAdmin) {
        Stock stock = stockRepository.findByStockCode(symbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다."));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Board board = boardRepository.findByBoardCode(STOCK_DISCUSSION_BOARD_CODE)
                .orElseThrow(() -> new IllegalArgumentException("종목 토론 게시판이 존재하지 않습니다."));

        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }

        boolean isNotice = isAdmin && Boolean.TRUE.equals(request.getIsNotice());

        Post post = Post.builder()
                .board(board)
                .user(user)
                .stock(isNotice ? null : stock)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .reportCount(0)
                .status("NORMAL")
                .isNotice(isNotice)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return postRepository.save(post).getId();
    }

    @Transactional(readOnly = true)
    public CommunityPostDetailResponseDto getPostDetail(Long postId, String email) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        Stock stock = post.getStock();

        List<CommunityCommentResponseDto> comments = commentRepository
                .findByPostIdAndStatusOrderByCreatedAtAsc(postId, "NORMAL")
                .stream()
                .map(comment -> CommunityCommentResponseDto.builder()
                        .commentId(comment.getId())
                        .userId(comment.getUser().getId())
                        .nickname(comment.getUser().getNickname())
                        .content(comment.getContent())
                        .createdAt(comment.getCreatedAt())
                        .build())
                .toList();

        boolean likedByCurrentUser = false;

        if (email != null) {
            User loginUser = userRepository.findByEmail(email).orElse(null);
            if (loginUser != null) {
                likedByCurrentUser = postLikeRepository.existsByPostIdAndUserId(postId, loginUser.getId());
            }
        }

        return CommunityPostDetailResponseDto.builder()
                .postId(post.getId())
                .stockId(stock != null ? stock.getId() : null)
                .stockCode(stock != null ? stock.getStockCode() : null)
                .stockName(stock != null ? stock.getStockName() : null)
                .userId(post.getUser().getId())
                .nickname(post.getUser().getNickname())
                .hasBoughtStock(stock != null && hasBoughtStock(post.getUser().getId(), stock.getId()))
                .title(post.getTitle())
                .content(post.getContent())
                .commentCount(post.getCommentCount())
                .likeCount(post.getLikeCount())
                .viewCount(post.getViewCount())
                .isNotice(post.getIsNotice())
                .createdAt(post.getCreatedAt())
                .likedByCurrentUser(likedByCurrentUser)
                .comments(comments)
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
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (post.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("본인 글은 추천할 수 없습니다.");
        }

        boolean alreadyLiked = postLikeRepository.existsByPostIdAndUserId(postId, user.getId());
        if (alreadyLiked) {
            throw new IllegalArgumentException("이미 추천한 게시글입니다.");
        }

        PostLike postLike = PostLike.builder()
                .post(post)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();

        postLikeRepository.save(postLike);
        post.increaseLikeCount();
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

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .parentComment(null)
                .content(request.getContent().trim())
                .status("NORMAL")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        post.increaseCommentCount();
        return commentRepository.save(comment).getId();
    }

    @Transactional(readOnly = true)
    public List<CommunityCommentResponseDto> getComments(Long postId) {
        return commentRepository.findByPostIdAndStatusOrderByCreatedAtAsc(postId, "NORMAL")
                .stream()
                .map(comment -> CommunityCommentResponseDto.builder()
                        .commentId(comment.getId())
                        .userId(comment.getUser().getId())
                        .nickname(comment.getUser().getNickname())
                        .content(comment.getContent())
                        .createdAt(comment.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void updatePost(Long postId, CommunityPostUpdateRequestDto request, String email, boolean isAdmin) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        User loginUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        boolean isOwner = post.getUser().getId().equals(loginUser.getId());

        if (!isAdmin && !isOwner) {
            throw new IllegalArgumentException("본인 게시글만 수정할 수 있습니다.");
        }

        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }

        boolean isNotice = post.getIsNotice();
        if (isAdmin) {
            isNotice = Boolean.TRUE.equals(request.getIsNotice());
        }

        post.updatePost(request.getTitle().trim(), request.getContent().trim(), isNotice);
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

        boolean alreadyReported = postReportRepository.existsByPostIdAndReporterUserId(postId, reporter.getId());
        if (alreadyReported) {
            throw new IllegalArgumentException("이미 신고한 게시글입니다.");
        }

        PostReport postReport = PostReport.builder()
                .post(post)
                .reporterUser(reporter)
                .reason(request.getReason().trim())
                .detail(normalizeDetail(request.getDetail()))
                .reportStatus("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        postReportRepository.save(postReport);
        post.increaseReportCount();
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

        boolean alreadyReported = commentReportRepository.existsByCommentIdAndReporterUserId(commentId, reporter.getId());
        if (alreadyReported) {
            throw new IllegalArgumentException("이미 신고한 댓글입니다.");
        }

        CommentReport commentReport = CommentReport.builder()
                .comment(comment)
                .reporterUser(reporter)
                .reason(request.getReason().trim())
                .detail(normalizeDetail(request.getDetail()))
                .reportStatus("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        commentReportRepository.save(commentReport);
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

    private boolean hasBoughtStock(Long userId, Long stockId) {
        return orderRepository
                .existsByAccountUserIdAndStockIdAndOrderSideIgnoreCaseAndOrderStatusIgnoreCase(
                        userId,
                        stockId,
                        "BUY",
                        "COMPLETED"
                ) == 1;
    }
}