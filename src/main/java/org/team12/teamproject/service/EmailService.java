package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    private final Map<String, String> emailCodeMap = new HashMap<>();
    private final Map<String, Boolean> verifiedEmailMap = new HashMap<>();

    // 인증번호 생성
    public String createCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    // 이메일 발송
    public void sendEmail(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("회원가입 인증번호");
        message.setText("인증번호는 [" + code + "] 입니다.");
        mailSender.send(message);
    }

    // 인증번호 저장
    public void saveCode(String email, String code) {
        String cleanEmail = email != null ? email.trim().toUpperCase() : null;
        emailCodeMap.put(cleanEmail, code);
        verifiedEmailMap.put(cleanEmail, false);
    }

    // 인증번호 검증
    public boolean verifyCode(String email, String code) {
        String cleanEmail = email != null ? email.trim().toUpperCase() : null;
        String savedCode = emailCodeMap.get(cleanEmail);
        if (savedCode == null) return false;
        
        boolean result = savedCode.equals(code);
        if (result) {
            verifiedEmailMap.put(cleanEmail, true);
        }
        return result;
    }

    // 이메일 인증 여부 확인
    public boolean isVerified(String email) {
        String cleanEmail = email != null ? email.trim().toUpperCase() : null;
        return verifiedEmailMap.getOrDefault(cleanEmail, false);
    }

    // 인증 정보 삭제
    public void clearVerification(String email) {
        String cleanEmail = email != null ? email.trim().toUpperCase() : null;
        emailCodeMap.remove(cleanEmail);
        verifiedEmailMap.remove(cleanEmail);
    }
}