package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.CompetitionDetailResponseDto;
import org.team12.teamproject.dto.CompetitionListResponseDto;
import org.team12.teamproject.service.CompetitionService;

import java.util.List;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;

    @GetMapping
    public ResponseEntity<List<CompetitionListResponseDto>> getCompetitionList() {
        return ResponseEntity.ok(competitionService.getCompetitionList());
    }

    @GetMapping("/{competitionId}")
    public ResponseEntity<CompetitionDetailResponseDto> getCompetitionDetail(@PathVariable Long competitionId) {
        return ResponseEntity.ok(competitionService.getCompetitionDetail(competitionId));
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
}