package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.LoginRequestDto;
import org.team12.teamproject.dto.LoginResponseDto;
import org.team12.teamproject.dto.SignupRequestDto;
import org.team12.teamproject.dto.UserProfileResponseDto;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public String signup(SignupRequestDto dto) {
        String email = dto.getEmail().trim().toUpperCase();

        if (!emailService.isVerified(email)) {
            return "이메일 인증을 먼저 완료해주세요.";
        }

        if (userRepository.countByEmail(email) > 0) {
            return "이미 사용 중인 이메일입니다.";
        }

        if (userRepository.countByNickname(dto.getNickname()) > 0) {
            return "이미 사용 중인 닉네임입니다.";
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        User user = User.builder()
                .email(email)
                .passwordHash(encodedPassword)
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

        // 회원가입 시 기본 계좌 생성 (1,000만원 시작)
        createDefaultAccount(user);

        emailService.clearVerification(dto.getEmail());

        return "회원가입 완료";
    }

    private void createDefaultAccount(User user) {
        Account account = Account.builder()
                .user(user)
                .accountType("MAIN")
                .accountName(user.getNickname() + "의 기본 계좌")
                .cashBalance(new BigDecimal("5000000")) // 500만원
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        accountRepository.save(account);
    }

    public LoginResponseDto login(LoginRequestDto dto) {

        User user = userRepository.findByEmail(dto.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new RuntimeException("회원정보가 일치하지 않습니다."));

        if (!matchesPassword(dto.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("회원정보가 일치하지 않습니다.");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException("비활성화된 계정입니다.");
        }

        return new LoginResponseDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                "로그인 성공");
    }

    public String checkEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "이메일을 입력해주세요.";
        }

        String cleanEmail = email.trim().toUpperCase();
        if (userRepository.countByEmail(cleanEmail) > 0) {
            return "이미 사용 중인 이메일입니다.";
        }

        return "사용 가능한 이메일입니다.";
    }

    public UserProfileResponseDto getUserProfile(String email) {
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("회원정보가 일치하지 않습니다."));

        return toUserProfile(user);
    }

    public List<UserProfileResponseDto> getUserList() {
        return userRepository.findAll().stream()
                .map(this::toUserProfile)
                .toList();
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }

        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$")
                || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        // TODO: 배포 전 이 평문 비밀번호 fallback 로직은 반드시 제거할 것.
        return storedPassword.equals(rawPassword);
    }

    private UserProfileResponseDto toUserProfile(User user) {
        List<Account> accounts = accountRepository.findByUserId(user.getId());

        // 계좌가 없는 기존 유저 등을 위한 자동 생성 로직
        if (accounts.isEmpty()) {
            createDefaultAccount(user);
            accounts = accountRepository.findByUserId(user.getId());
        }

        Long accountId = accounts.get(0).getId();

        return UserProfileResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .accountId(accountId)
                .accountCount(accounts.size())
                .build();
    }
}