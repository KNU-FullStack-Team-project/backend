package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.CompetitionSaveRequestDto;
import org.team12.teamproject.service.CompetitionService;

@RestController
@RequestMapping("/api/admin/competitions")
@RequiredArgsConstructor
public class AdminCompetitionController {

    private final CompetitionService competitionService;

    @PostMapping
    public ResponseEntity<String> createCompetition(
            @RequestParam Long adminUserId,
            @RequestBody CompetitionSaveRequestDto request
    ) {
        try {
            Long competitionId = competitionService.createCompetition(adminUserId, request);
            return ResponseEntity.ok("대회 생성 완료 (ID: " + competitionId + ")");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{competitionId}")
    public ResponseEntity<String> updateCompetition(
            @PathVariable Long competitionId,
            @RequestBody CompetitionSaveRequestDto request
    ) {
        try {
            competitionService.updateCompetition(competitionId, request);
            return ResponseEntity.ok("대회 수정 완료");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{competitionId}")
    public ResponseEntity<String> deleteCompetition(@PathVariable Long competitionId) {
        try {
            competitionService.deleteCompetition(competitionId);
            return ResponseEntity.ok("대회 삭제 완료");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}