package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.CommunityCommentCreateRequestDto;
import org.team12.teamproject.dto.CommunityCommentResponseDto;
import org.team12.teamproject.dto.CommunityPostCreateRequestDto;
import org.team12.teamproject.dto.CommunityPostDetailResponseDto;
import org.team12.teamproject.dto.CommunityPostResponseDto;
import org.team12.teamproject.dto.CommunityPostUpdateRequestDto;
import org.team12.teamproject.dto.CommunityReportRequestDto;
import org.team12.teamproject.service.CommunityService;

import java.util.List;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping("/stocks/{symbol}/posts")
    public ResponseEntity<List<CommunityPostResponseDto>> getStockPosts(
            @PathVariable String symbol
    ) {
        System.out.println("=== getStockPosts controller hit: " + symbol);
        return ResponseEntity.ok(communityService.getStockPosts(symbol));
    }

    @PostMapping("/stocks/{symbol}/posts")
    public ResponseEntity<?> createStockPost(
            @PathVariable String symbol,
            @RequestBody CommunityPostCreateRequestDto request,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            String email = authentication.getName();
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));

            Long postId = communityService.createStockPost(symbol, request, email, isAdmin);
            return ResponseEntity.ok(postId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<CommunityPostDetailResponseDto> getPostDetail(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(communityService.getPostDetail(postId, email));
    }

    @PostMapping("/posts/{postId}/view")
    public ResponseEntity<?> increaseViewCount(@PathVariable Long postId) {
        try {
            communityService.increaseViewCount(postId);
            return ResponseEntity.ok("조회수가 증가되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<?> likePost(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            String email = authentication.getName();
            communityService.likePost(postId, email);
            return ResponseEntity.ok("추천이 반영되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/posts/{postId}/report")
    public ResponseEntity<?> reportPost(
            @PathVariable Long postId,
            @RequestBody CommunityReportRequestDto request,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            communityService.reportPost(postId, request, authentication.getName());
            return ResponseEntity.ok("게시글 신고가 접수되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/posts/{postId}")
    public ResponseEntity<?> updatePost(
            @PathVariable Long postId,
            @RequestBody CommunityPostUpdateRequestDto request,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            String email = authentication.getName();
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));

            communityService.updatePost(postId, request, email, isAdmin);
            return ResponseEntity.ok("게시글이 수정되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<?> deletePost(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            String email = authentication.getName();
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));

            communityService.deletePost(postId, email, isAdmin);
            return ResponseEntity.ok("게시글이 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommunityCommentResponseDto>> getComments(@PathVariable Long postId) {
        return ResponseEntity.ok(communityService.getComments(postId));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<?> createComment(
            @PathVariable Long postId,
            @RequestBody CommunityCommentCreateRequestDto request,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            String email = authentication.getName();
            Long commentId = communityService.createComment(postId, request, email);
            return ResponseEntity.ok(commentId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<?> reportComment(
            @PathVariable Long commentId,
            @RequestBody CommunityReportRequestDto request,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            communityService.reportComment(commentId, request, authentication.getName());
            return ResponseEntity.ok("댓글 신고가 접수되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable Long commentId,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            String email = authentication.getName();
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));

            communityService.deleteComment(commentId, email, isAdmin);
            return ResponseEntity.ok("댓글이 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/notices")
    public ResponseEntity<List<CommunityPostResponseDto>> getNoticePosts() {
        return ResponseEntity.ok(communityService.getNoticePosts());
    }
}