package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.InquiryCreateRequestDto;
import org.team12.teamproject.dto.InquiryResponseDto;
import org.team12.teamproject.service.InquiryService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping
    public ResponseEntity<Long> createInquiry(@RequestBody InquiryCreateRequestDto requestDto, Principal principal) {
        return ResponseEntity.ok(inquiryService.createInquiry(requestDto, principal.getName()));
    }

    @GetMapping("/my")
    public ResponseEntity<List<InquiryResponseDto>> getMyInquiries(Principal principal) {
        return ResponseEntity.ok(inquiryService.getMyInquiries(principal.getName()));
    }

    @GetMapping("/all")
    public ResponseEntity<List<InquiryResponseDto>> getAllInquiries(Principal principal) {
        return ResponseEntity.ok(inquiryService.getAllInquiries(principal.getName()));
    }

    @PutMapping("/{id}/reply")
    public ResponseEntity<Void> replyInquiry(@PathVariable Long id, @RequestBody Map<String, String> body, Principal principal) {
        inquiryService.replyInquiry(id, body.get("answer"), principal.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(Principal principal) {
        return ResponseEntity.ok(inquiryService.getUnreadCount(principal.getName()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, Principal principal) {
        inquiryService.markAsRead(id, principal.getName());
        return ResponseEntity.ok().build();
    }
}
