package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.EmailRequestDto;
import org.team12.teamproject.service.EmailService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/email")
@CrossOrigin(origins = "http://localhost:5173")
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send/signup")
    public Map<String, Object> sendSignupEmail(@RequestBody EmailRequestDto dto) {
        Map<String, Object> result = new HashMap<>();

        try {
            String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim() : "";

            if (cleanEmail.isEmpty()) {
                result.put("success", false);
                result.put("message", "이메일을 입력해주세요.");
                return result;
            }

            String code = emailService.createCode();
            emailService.saveCode(cleanEmail, code);
            emailService.sendSignupCodeEmail(cleanEmail, code);

            result.put("success", true);
            result.put("message", "인증번호가 이메일로 발송되었습니다.");
            result.put("remainingSeconds", 300);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "이메일 발송 중 오류가 발생했습니다.");
            return result;
        }
    }

    @PostMapping("/send/password-reset")
    public Map<String, Object> sendPasswordResetEmail(@RequestBody EmailRequestDto dto) {
        Map<String, Object> result = new HashMap<>();

        try {
            String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim() : "";

            if (cleanEmail.isEmpty()) {
                result.put("success", false);
                result.put("message", "이메일을 입력해주세요.");
                return result;
            }

            String code = emailService.createCode();
            emailService.saveCode(cleanEmail, code);
            emailService.sendPasswordResetCodeEmail(cleanEmail, code);

            result.put("success", true);
            result.put("message", "인증번호가 이메일로 발송되었습니다.");
            result.put("remainingSeconds", 300);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "이메일 발송 중 오류가 발생했습니다.");
            return result;
        }
    }

    @PostMapping("/verify")
    public Map<String, Object> verifyEmail(@RequestBody EmailRequestDto dto) {
        Map<String, Object> result = new HashMap<>();

        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim() : "";
        String code = dto.getCode() != null ? dto.getCode().trim() : "";

        if (cleanEmail.isEmpty()) {
            result.put("success", false);
            result.put("message", "이메일을 입력해주세요.");
            return result;
        }

        if (code.isEmpty()) {
            result.put("success", false);
            result.put("message", "인증번호를 입력해주세요.");
            return result;
        }

        if (emailService.isCodeExpired(cleanEmail)) {
            result.put("success", false);
            result.put("message", "인증시간이 만료되었습니다. 다시 요청해주세요.");
            return result;
        }

        boolean verified = emailService.verifyCode(cleanEmail, code);

        if (verified) {
            result.put("success", true);
            result.put("message", "인증이 완료되었습니다.");
        } else {
            result.put("success", false);
            result.put("message", "인증번호가 일치하지 않습니다.");
        }

        return result;
    }

    @PostMapping("/remaining-time")
    public Map<String, Object> getRemainingTime(@RequestBody EmailRequestDto dto) {
        Map<String, Object> result = new HashMap<>();

        String cleanEmail = dto.getEmail() != null ? dto.getEmail().trim() : "";

        if (cleanEmail.isEmpty()) {
            result.put("success", false);
            result.put("remainingSeconds", 0);
            return result;
        }

        long remainingSeconds = emailService.getRemainingSeconds(cleanEmail);

        result.put("success", true);
        result.put("remainingSeconds", remainingSeconds);
        return result;
    }
}