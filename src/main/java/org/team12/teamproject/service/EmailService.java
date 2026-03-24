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

    private Map<String, Boolean> verifiedEmailMap = new HashMap<>();

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
        emailCodeMap.put(email, code);
    }

    // 인증번호 검증
    public boolean verifyCode(String email, String code) {
        String savedCode = emailCodeMap.get(email);
        if (savedCode == null) return false;
        return savedCode.equals(code);
    }

    // 인증 성공 처리
    public void setVerified(String email) {
        verifiedEmailMap.put(email, true);
}

    // 인증 여부 확인
    public boolean isVerified(String email) {
        return verifiedEmailMap.getOrDefault(email, false);
    }
}