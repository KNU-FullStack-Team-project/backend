package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.team12.teamproject.service.CommunityProfileService;

@RestController
@RequestMapping("/api/admin/community")
@RequiredArgsConstructor
public class AdminCommunityController {

    private final CommunityProfileService communityProfileService;

    @GetMapping("/users")
    public ResponseEntity<?> getAdminCommunityUsers(
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));

            if (!isAdmin) {
                return ResponseEntity.status(403).body("관리자만 접근할 수 있습니다.");
            }

            return ResponseEntity.ok(
                    communityProfileService.getAdminCommunityUsers()
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}