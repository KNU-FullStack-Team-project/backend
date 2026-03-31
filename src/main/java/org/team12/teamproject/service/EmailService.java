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
        emailCodeMap.put(email, code);
        verifiedEmailMap.put(email, false);
    }

    // 인증번호 검증
    public boolean verifyCode(String email, String code) {
        String savedCode = emailCodeMap.get(email);
        if (savedCode == null) return false;

        boolean result = savedCode.equals(code);
        if (result) {
            verifiedEmailMap.put(email, true);
        }
        return result;
    }

    // 이메일 인증 여부 확인
    public boolean isVerified(String email) {
        return verifiedEmailMap.getOrDefault(email, false);
    }

    // 인증 정보 삭제
    public void clearVerification(String email) {
        emailCodeMap.remove(email);
        verifiedEmailMap.remove(email);
    }
}