package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.SignupRequestDto;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public String signup(SignupRequestDto dto) {

        // 1. 이메일 인증 확인
        if (!emailService.isVerified(dto.getEmail())) {
            return "이메일 인증을 먼저 완료해주세요.";
        }

        // 2. 이메일 중복 확인
        if (userRepository.existsByEmail(dto.getEmail())) {
            return "이미 사용 중인 이메일입니다.";
        }

        // 3. 닉네임 중복 확인
        if (userRepository.existsByNickname(dto.getNickname())) {
            return "이미 사용 중인 닉네임입니다.";
        }

        // 4. 회원 생성
        User user = User.builder()
                .email(dto.getEmail())
                .passwordHash(dto.getPassword()) // 나중에 BCrypt로 암호화 필요
                .nickname(dto.getNickname())
                .profileImageUrl(null)
                .role("USER")
                .status("ACTIVE")
                .marketingConsent(dto.getMarketingConsent() != null && dto.getMarketingConsent())
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 5. 저장
        userRepository.save(user);

        // 6. 인증 정보 삭제
        emailService.clearVerification(dto.getEmail());

        return "회원가입 완료";
    }
}