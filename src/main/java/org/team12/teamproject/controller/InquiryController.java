package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.InquiryCreateRequestDto;
import org.team12.teamproject.service.InquiryService;

import java.security.Principal;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping
    public ResponseEntity<Long> createInquiry(@RequestBody InquiryCreateRequestDto requestDto, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        Long inquiryId = inquiryService.createInquiry(requestDto, principal.getName());
        return ResponseEntity.ok(inquiryId);
    }
}
