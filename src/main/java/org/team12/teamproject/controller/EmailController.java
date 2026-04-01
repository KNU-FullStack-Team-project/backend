package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.EmailRequestDto;
import org.team12.teamproject.service.EmailService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/email")
@CrossOrigin(origins = "http://localhost:5173")
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send")
    public String sendEmail(@RequestBody EmailRequestDto dto) {
        try {
            String cleanEmail = dto.getEmail().trim();
            String code = emailService.createCode();
            emailService.sendEmail(cleanEmail, code);
            emailService.saveCode(cleanEmail, code);
            return "인증코드 발송 완료";
        } catch (Exception e) {
            e.printStackTrace();
            return "메일 발송 실패: " + e.getMessage();
        }
    }

    @PostMapping("/verify")
    public String verifyEmail(@RequestBody EmailRequestDto dto) {
        String cleanEmail = dto.getEmail().trim();
        boolean result = emailService.verifyCode(cleanEmail, dto.getCode());

        if (result) {
            return "인증 성공";
        } else {
            return "인증 실패";
        }
    }
}