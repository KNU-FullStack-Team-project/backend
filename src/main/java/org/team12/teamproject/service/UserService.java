package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.ChangePasswordRequestDto;
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
        String email = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        if (email.isEmpty()) return "이메일을 입력해주세요.";

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
        String email = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(email)
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

        String cleanEmail = email.trim().toLowerCase();
        if (userRepository.countByEmail(cleanEmail) > 0) {
            return "이미 사용 중인 이메일입니다.";
        }

        return "사용 가능한 이메일입니다.";
    }

    public UserProfileResponseDto getUserProfile(String email) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new RuntimeException("회원정보가 일치하지 않습니다."));

        return toUserProfile(user);
    }

    public String changePassword(ChangePasswordRequestDto dto) {
        String email = dto.getEmail() != null ? dto.getEmail().trim().toLowerCase() : "";
        String currentPassword = dto.getCurrentPassword();
        String newPassword = dto.getNewPassword();

        if (email.isEmpty()) {
            throw new RuntimeException("회원 정보를 찾을 수 없습니다.");
        }

        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("비밀번호를 모두 입력해 주세요.");
        }

        if (!isValidPassword(newPassword)) {
            throw new RuntimeException("새 비밀번호는 8자 이상, 영문/숫자/특수문자를 포함해야 합니다.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("회원정보가 일치하지 않습니다."));

        if (!matchesPassword(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("현재 비밀번호가 일치하지 않습니다.");
        }

        if (matchesPassword(newPassword, user.getPasswordHash())) {
            throw new RuntimeException("새 비밀번호는 기존 비밀번호와 다른 값으로 설정해 주세요.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return "비밀번호가 변경되었습니다.";
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

        return passwordEncoder.matches(rawPassword, storedPassword);
    }

    private boolean isValidPassword(String value) {
        if (value == null || value.length() < 8) {
            return false;
        }

        boolean hasLetter = value.chars().anyMatch(Character::isLetter);
        boolean hasDigit = value.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));

        return hasLetter && hasDigit && hasSpecial;
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
