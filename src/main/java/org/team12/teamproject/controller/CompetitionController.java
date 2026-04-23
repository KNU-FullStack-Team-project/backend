package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.CompetitionDetailResponseDto;
import org.team12.teamproject.dto.CompetitionListResponseDto;
import org.team12.teamproject.dto.CompetitionParticipantDto;
import org.team12.teamproject.dto.CompetitionRankingResponseDto;
import org.team12.teamproject.service.CompetitionService;

import java.util.List;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;

    @GetMapping
    public ResponseEntity<List<CompetitionListResponseDto>> getCompetitionList(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                competitionService.getCompetitionList(isAdmin(authentication))
        );
    }

    @GetMapping("/{competitionId}")
    public ResponseEntity<CompetitionDetailResponseDto> getCompetitionDetail(
            @PathVariable Long competitionId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                competitionService.getCompetitionDetail(
                        competitionId,
                        isAdmin(authentication)
                )
        );
    }

    @PostMapping("/{competitionId}/join")
    public ResponseEntity<String> joinCompetition(
            @PathVariable Long competitionId,
            @RequestParam Long userId
    ) {
        try {
            competitionService.joinCompetition(competitionId, userId);
            return ResponseEntity.ok("참가 완료");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<Long>> getMyCompetitions(@RequestParam Long userId) {
        return ResponseEntity.ok(competitionService.getMyCompetitionIds(userId));
    }

    @GetMapping("/{competitionId}/participants")
    public ResponseEntity<List<CompetitionParticipantDto>> getParticipants(
            @PathVariable Long competitionId
    ) {
        return ResponseEntity.ok(competitionService.getParticipants(competitionId));
    }

    @GetMapping("/{competitionId}/ranking")
    public ResponseEntity<?> getCompetitionRanking(
            @PathVariable Long competitionId
    ) {
        try {
            List<CompetitionRankingResponseDto> ranking =
                    competitionService.getCompetitionRanking(competitionId);
            return ResponseEntity.ok(ranking);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{competitionId}/visibility")
    public ResponseEntity<?> updateCompetitionVisibility(
            @PathVariable Long competitionId,
            @RequestParam boolean isPublic,
            Authentication authentication
    ) {
        try {
            if (!isAdmin(authentication)) {
                return ResponseEntity.status(403).body("관리자만 공개/비공개를 변경할 수 있습니다.");
            }

            competitionService.updateCompetitionVisibility(competitionId, isPublic);
            return ResponseEntity.ok(isPublic ? "대회를 공개로 변경했습니다." : "대회를 비공개로 변경했습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{competitionId}")
    public ResponseEntity<?> deleteCompetition(
            @PathVariable Long competitionId,
            Authentication authentication
    ) {
        try {
            if (!isAdmin(authentication)) {
                return ResponseEntity.status(403).body("관리자만 대회를 삭제할 수 있습니다.");
            }

            competitionService.deleteCompetition(competitionId);
            return ResponseEntity.ok("대회 삭제 처리 완료");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));
    }
}