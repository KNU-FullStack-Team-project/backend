package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.LoginRequestDto;
import org.team12.teamproject.dto.LoginResponseDto;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    public LoginResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new RuntimeException("이메일이 존재하지 않습니다."));

        if (user.getPasswordHash() == null || !user.getPasswordHash().equals(request.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException("비활성화된 계정입니다.");
        }

        return new LoginResponseDto(
        user.getId(),          // 👈 이거 중요
        user.getEmail(),
        user.getNickname(),
        user.getRole(),
        "로그인 성공"
);
    }
}