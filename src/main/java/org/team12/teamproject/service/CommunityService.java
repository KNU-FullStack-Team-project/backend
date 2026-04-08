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
import org.team12.teamproject.entity.Board;
import org.team12.teamproject.entity.Comment;
import org.team12.teamproject.entity.Post;
import org.team12.teamproject.entity.PostLike;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.BoardRepository;
import org.team12.teamproject.repository.CommentRepository;
import org.team12.teamproject.repository.OrderRepository;
import org.team12.teamproject.repository.PostLikeRepository;
import org.team12.teamproject.repository.PostRepository;
import org.team12.teamproject.repository.StockRepository;
import org.team12.teamproject.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

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

    private static final String STOCK_DISCUSSION_BOARD_CODE = "STOCK_DISCUSSION";

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> getStockPosts(String symbol) {
        Stock stock = stockRepository.findByStockCode(symbol)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다."));

        return postRepository.findByStockIdAndStatusOrderByCreatedAtDesc(stock.getId(), "NORMAL")
                .stream()
                .map(post -> CommunityPostResponseDto.builder()
                        .postId(post.getId())
                        .stockId(stock.getId())
                        .stockCode(stock.getStockCode())
                        .stockName(stock.getStockName())
                        .userId(post.getUser().getId())
                        .nickname(post.getUser().getNickname())
                        .hasBoughtStock(hasBoughtStock(post.getUser().getId(), stock.getId()))
                        .title(post.getTitle())
                        .commentCount(post.getCommentCount())
                        .viewCount(post.getViewCount())
                        .likeCount(post.getLikeCount())
                        .createdAt(post.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public Long createStockPost(String symbol, CommunityPostCreateRequestDto request, String email) {
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

        Post post = Post.builder()
                .board(board)
                .user(user)
                .stock(stock)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .reportCount(0)
                .status("NORMAL")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return postRepository.save(post).getId();
    }

    @Transactional
    public CommunityPostDetailResponseDto getPostDetail(Long postId, String email) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        post.increaseViewCount();

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
                .createdAt(post.getCreatedAt())
                .likedByCurrentUser(likedByCurrentUser)
                .comments(comments)
                .build();
    }

    @Transactional
    public void likePost(Long postId, String email) {
        Post post = postRepository.findByIdAndStatus(postId, "NORMAL")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

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

    private boolean hasBoughtStock(Long userId, Long stockId) {
        return orderRepository
                .existsByAccountUserIdAndStockIdAndOrderSideIgnoreCaseAndOrderStatusIgnoreCase(
                        userId,
                        stockId,
                        "BUY",
                        "COMPLETED"
                ) == 1;
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

        post.updatePost(request.getTitle().trim(), request.getContent().trim());
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
}