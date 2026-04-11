package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.team12.teamproject.dto.CommunityAttachmentResponseDto;
import org.team12.teamproject.service.FileStorageService;

@RestController
@RequestMapping("/api/community/uploads")
@RequiredArgsConstructor
public class CommunityUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping("/images")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            CommunityAttachmentResponseDto response =
                    fileStorageService.storeImage(file, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/files")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }

            CommunityAttachmentResponseDto response =
                    fileStorageService.storeFile(file, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}