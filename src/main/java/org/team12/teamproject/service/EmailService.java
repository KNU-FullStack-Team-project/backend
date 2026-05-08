package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    private final Map<String, String> emailCodeMap = new ConcurrentHashMap<>();
    private final Map<String, Long> emailExpireMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> verifiedEmailMap = new ConcurrentHashMap<>();

    // 인증번호 생성
    public String createCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    // 회원가입 인증 메일 발송
    public void sendSignupCodeEmail(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("회원가입 인증번호");
        message.setText(
                "안녕하세요.\n\n" +
                "회원가입 인증번호는 [" + code + "] 입니다.\n" +
                "인증번호는 5분 동안 유효합니다.\n\n" +
                "감사합니다."
        );
        try {
            mailSender.send(message);
            log.info("회원가입 인증 메일 발송 성공: {}", email);
        } catch (MailException e) {
            log.error("회원가입 인증 메일 발송 실패: {}", email, e);
            throw new RuntimeException("메일 발송 중 오류가 발생했습니다.");
        }
    }

    // 비밀번호 재설정 인증 메일 발송
    public void sendPasswordResetCodeEmail(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("비밀번호 재설정 인증번호");
        message.setText(
                "안녕하세요.\n\n" +
                "비밀번호 재설정 인증번호는 [" + code + "] 입니다.\n" +
                "인증번호는 5분 동안 유효합니다.\n\n" +
                "감사합니다."
        );
        try {
            mailSender.send(message);
            log.info("비밀번호 재설정 메일 발송 성공: {}", email);
        } catch (MailException e) {
            log.error("비밀번호 재설정 메일 발송 실패: {}", email, e);
            throw new RuntimeException("메일 발송 중 오류가 발생했습니다.");
        }
    }

    // 인증번호 저장 + 만료시간 5분 설정
    public void saveCode(String email, String code) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : null;

        if (cleanEmail != null && !cleanEmail.isEmpty()) {
            emailCodeMap.put(cleanEmail, code);
            emailExpireMap.put(cleanEmail, System.currentTimeMillis() + (5 * 60 * 1000));
            verifiedEmailMap.put(cleanEmail, false);
        }
    }

    // 인증번호 검증
    public boolean verifyCode(String email, String code) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : null;

        if (cleanEmail == null || cleanEmail.isEmpty()) {
            return false;
        }

        String savedCode = emailCodeMap.get(cleanEmail);
        Long expireTime = emailExpireMap.get(cleanEmail);

        if (savedCode == null || expireTime == null) {
            return false;
        }

        // 만료 여부 확인
        if (System.currentTimeMillis() > expireTime) {
            clearVerification(cleanEmail);
            return false;
        }

        boolean result = savedCode.equals(code);

        if (result) {
            verifiedEmailMap.put(cleanEmail, true);
        }

        return result;
    }

    // 이메일 인증 여부 확인
    public boolean isVerified(String email) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : null;

        if (cleanEmail == null || cleanEmail.isEmpty()) {
            return false;
        }

        return verifiedEmailMap.getOrDefault(cleanEmail, false);
    }

    // 만료 여부 확인
    public boolean isCodeExpired(String email) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : null;

        if (cleanEmail == null || cleanEmail.isEmpty()) {
            return true;
        }

        Long expireTime = emailExpireMap.get(cleanEmail);

        if (expireTime == null) {
            return true;
        }

        return System.currentTimeMillis() > expireTime;
    }

    // 남은 시간(초) 반환
    public long getRemainingSeconds(String email) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : null;

        if (cleanEmail == null || cleanEmail.isEmpty()) {
            return 0;
        }

        Long expireTime = emailExpireMap.get(cleanEmail);

        if (expireTime == null) {
            return 0;
        }

        long remainingMillis = expireTime - System.currentTimeMillis();
        return Math.max(0, remainingMillis / 1000);
    }

    // 인증 정보 삭제
    public void clearVerification(String email) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : null;

        if (cleanEmail != null && !cleanEmail.isEmpty()) {
            emailCodeMap.remove(cleanEmail);
            emailExpireMap.remove(cleanEmail);
            verifiedEmailMap.remove(cleanEmail);
        }
    }
}