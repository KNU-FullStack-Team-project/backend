package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.LoginRequestDto;
import org.team12.teamproject.dto.SignupRequestDto;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder; // 추가

    public String signup(SignupRequestDto dto) {

        if (!emailService.isVerified(dto.getEmail())) {
            return "이메일 인증을 먼저 완료해주세요.";
        }

        if (userRepository.countByEmail(dto.getEmail()) > 0) {
            return "이미 사용 중인 이메일입니다.";
        }

        if (userRepository.countByNickname(dto.getNickname()) > 0) {
            return "이미 사용 중인 닉네임입니다.";
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        User user = User.builder()
                .email(dto.getEmail())
                .passwordHash(encodedPassword) // 암호화된 비밀번호 저장
                .nickname(dto.getNickname())
                .profileImageUrl(null)
                .role("USER")
                .status("ACTIVE")
                .marketingConsent(dto.getMarketingConsent() != null && dto.getMarketingConsent())
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        emailService.clearVerification(dto.getEmail());

        return "회원가입 완료";
    }
    public String login(LoginRequestDto dto) {

        User user = userRepository.findByEmail(dto.getEmail())
                    .orElse(null);

        if (user == null) {
            return "존재하지 않는 이메일입니다.";
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            return "비밀번호가 일치하지 않습니다.";
        }

        return "로그인 성공";
    }
}