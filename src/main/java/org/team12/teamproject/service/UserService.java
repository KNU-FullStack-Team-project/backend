package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.team12.teamproject.dto.ChangePasswordRequestDto;
import org.team12.teamproject.dto.ChangeNicknameRequestDto;
import org.team12.teamproject.dto.LoginRequestDto;
import org.team12.teamproject.dto.LoginResponseDto;
import org.team12.teamproject.dto.ResetPasswordRequestDto;
import org.team12.teamproject.dto.SignupRequestDto;
import org.team12.teamproject.dto.AdminUpdateUserRequestDto;
import org.team12.teamproject.dto.UserProfileResponseDto;
import org.team12.teamproject.dto.WithdrawUserRequestDto;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.security.JwtUtil;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.repository.UserRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final JwtUtil jwtUtil;

    @Transactional
    public String signup(SignupRequestDto dto) {
        String email = dto.getEmail() != null ? dto.getEmail().trim() : "";
        if (email.isEmpty()) return "이메일을 입력해주세요.";

        if (!emailService.isVerified(email)) {
            return "이메일 인증을 먼저 완료해주세요.";
        }

        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (isEmailInUseByActiveUser(email)) {
            return "이미 사용 중인 이메일입니다.";
        }

        if (existingUser != null && "QUIT".equalsIgnoreCase(existingUser.getStatus())) {
            if (!dto.getNickname().equals(existingUser.getNickname())
                    && userRepository.countByNickname(dto.getNickname()) > 0) {
                return "이미 사용 중인 닉네임입니다.";
            }

            resetUserAssets(existingUser.getId());

            existingUser.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
            existingUser.setNickname(dto.getNickname());
            existingUser.setRole("USER");
            existingUser.setStatus("ACTIVE");
            existingUser.setMarketingConsent(dto.getMarketingConsent() != null && dto.getMarketingConsent());
            existingUser.setEmailVerified(true);
            existingUser.setWithdrawnAt(null);
            existingUser.setSuspendedAt(null);
            existingUser.setDeletedAt(null);
            existingUser.setCreatedAt(LocalDateTime.now());
            existingUser.setUpdatedAt(LocalDateTime.now());
            userRepository.save(existingUser);

            createDefaultAccount(existingUser);

            emailService.clearVerification(dto.getEmail());

            return "회원가입 완료";
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

    private void resetUserAssets(Long userId) {
        jdbcTemplate.update("DELETE FROM competition_participants WHERE user_id = ?", userId);
        jdbcTemplate.update(
                "DELETE FROM orders WHERE account_id IN (SELECT account_id FROM accounts WHERE user_id = ?)",
                userId
        );
        jdbcTemplate.update(
                "DELETE FROM holdings WHERE account_id IN (SELECT account_id FROM accounts WHERE user_id = ?)",
                userId
        );
        jdbcTemplate.update("DELETE FROM accounts WHERE user_id = ?", userId);
    }

    public LoginResponseDto login(LoginRequestDto dto) {
        String email = dto.getEmail() != null ? dto.getEmail().trim() : "";
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("회원정보가 일치하지 않습니다."));

        if (!matchesPassword(dto.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("회원정보가 일치하지 않습니다.");
        }

        if ("QUIT".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException("회원정보가 일치하지 않습니다.");
        }

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException("정지된 계정입니다.");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException("비활성화된 계정입니다.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // 실제 JWT 생성
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());

        // 사용자의 기본 계좌 ID 조회 (마이페이지 등에서 즉시 사용 위함)
        List<Account> accounts = accountRepository.findByUserId(user.getId());
        Long accountId = accounts.isEmpty() ? null : accounts.get(0).getId();

        return new LoginResponseDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                "로그인 성공",
                token,
                accountId);
    }

    public String checkEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "이메일을 입력해주세요.";
        }

        String cleanEmail = email.trim();
        if (isEmailInUseByActiveUser(cleanEmail)) {
            return "이미 사용 중인 이메일입니다.";
        }

        return "사용 가능한 이메일입니다.";
    }

    public String checkNickname(String nickname, String email) {
        String cleanNickname = nickname != null ? nickname.trim() : "";
        String cleanEmail = email != null ? email.trim() : "";

        if (cleanNickname.isEmpty()) {
            return "닉네임을 입력해주세요.";
        }

        if (!cleanEmail.isEmpty()) {
            User user = userRepository.findByEmail(cleanEmail).orElse(null);
            if (user != null && cleanNickname.equals(user.getNickname())) {
                return "사용 가능한 닉네임입니다.";
            }
        }

        if (userRepository.countByNickname(cleanNickname) > 0) {
            return "이미 사용 중인 닉네임입니다.";
        }

        return "사용 가능한 닉네임입니다.";
    }

    public UserProfileResponseDto getUserProfile(String email) {
        String cleanEmail = email != null ? email.trim() : "";
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new RuntimeException("회원정보가 일치하지 않습니다."));

        return toUserProfile(user);
    }

    public UserProfileResponseDto updateProfileImage(String email, MultipartFile image) {
        String cleanEmail = email != null ? email.trim() : "";
        if (cleanEmail.isEmpty()) {
            throw new RuntimeException("회원 정보를 찾을 수 없습니다.");
        }

        if (image == null || image.isEmpty()) {
            throw new RuntimeException("프로필 사진을 선택해 주세요.");
        }

        String contentType = image.getContentType();
        if (!"image/png".equalsIgnoreCase(contentType)
                && !"image/jpeg".equalsIgnoreCase(contentType)
                && !"image/jpg".equalsIgnoreCase(contentType)) {
            throw new RuntimeException("PNG, JPG, JPEG 파일만 업로드할 수 있습니다.");
        }

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new RuntimeException("회원 정보를 찾을 수 없습니다."));

        try {
            Path profileDirectory = getProfileDirectory();
            Files.createDirectories(profileDirectory);

            BufferedImage profileImage = ImageIO.read(image.getInputStream());
            if (profileImage == null) {
                throw new RuntimeException("이미지 파일을 읽을 수 없습니다.");
            }

            Path profileImagePath = profileDirectory.resolve(user.getId() + ".png");
            ImageIO.write(profileImage, "png", profileImagePath.toFile());

            user.setProfileImageUrl("/profile/" + user.getId() + ".png");
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            return toUserProfile(user);
        } catch (IOException e) {
            throw new RuntimeException("프로필 사진 저장에 실패했습니다.");
        }
    }

    public String changePassword(ChangePasswordRequestDto dto) {
        String email = dto.getEmail() != null ? dto.getEmail().trim() : "";
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

    public UserProfileResponseDto changeNickname(ChangeNicknameRequestDto dto) {
        String email = dto.getEmail() != null ? dto.getEmail().trim() : "";
        String nickname = dto.getNickname() != null ? dto.getNickname().trim() : "";

        if (email.isEmpty()) {
            throw new RuntimeException("회원 정보를 찾을 수 없습니다.");
        }

        if (nickname.isEmpty()) {
            throw new RuntimeException("닉네임을 입력해주세요.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("회원정보가 일치하지 않습니다."));

        if (!nickname.equals(user.getNickname()) && userRepository.countByNickname(nickname) > 0) {
            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
        }

        user.setNickname(nickname);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return toUserProfile(user);
    }

    public String resetPassword(ResetPasswordRequestDto dto) {
        String email = dto.getEmail() != null ? dto.getEmail().trim() : "";
        String newPassword = dto.getNewPassword();

        if (email.isEmpty()) {
            return "이메일을 입력해주세요.";
        }

        if (newPassword == null || newPassword.isBlank()) {
            return "새 비밀번호를 입력해주세요.";
        }

        if (!emailService.isVerified(email)) {
            return "이메일 인증을 먼저 완료해주세요.";
        }

        if (!isValidPassword(newPassword)) {
            return "새 비밀번호는 8자 이상, 영문/숫자/특수문자를 포함해야 합니다.";
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("가입된 회원정보가 없습니다."));

        if (matchesPassword(newPassword, user.getPasswordHash())) {
            return "이전 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.";
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        emailService.clearVerification(email);

        return "비밀번호가 재설정되었습니다.";
    }

    public List<UserProfileResponseDto> getUserList() {
        return userRepository.findAll().stream()
                .map(this::toUserProfile)
                .toList();
    }

    public UserProfileResponseDto updateAdminUser(Long userId, AdminUpdateUserRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (!"USER".equals(dto.getRole()) && !"ADMIN".equals(dto.getRole())) {
            throw new RuntimeException("권한 값이 올바르지 않습니다.");
        }

        if (!"ACTIVE".equals(dto.getStatus()) && !"SUSPENDED".equals(dto.getStatus())) {
            throw new RuntimeException("상태 값이 올바르지 않습니다.");
        }

        user.setRole(dto.getRole());
        user.setStatus(dto.getStatus());
        user.setSuspendedAt("SUSPENDED".equals(dto.getStatus()) ? LocalDateTime.now() : null);
        user.setUpdatedAt(LocalDateTime.now());

        return toUserProfile(userRepository.save(user));
    }

    public String withdraw(WithdrawUserRequestDto dto) {
        String email = dto.getEmail() != null ? dto.getEmail().trim() : "";
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("회원정보가 일치하지 않습니다."));

        deleteProfileImage(user.getId());

        user.setProfileImageUrl(null);
        user.setStatus("QUIT");
        user.setWithdrawnAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return "회원 탈퇴가 완료되었습니다.";
    }

    private boolean isEmailInUseByActiveUser(String email) {
        return userRepository.findByEmail(email)
                .map(user -> !"QUIT".equalsIgnoreCase(user.getStatus()))
                .orElse(false);
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
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .accountId(accountId)
                .accountCount(accounts.size())
                .build();
    }

    private Path getProfileDirectory() {
        Path backendProfileDirectory = Paths.get("backend", "profile");
        if (Files.exists(Paths.get("backend")) || Files.exists(backendProfileDirectory)) {
            return backendProfileDirectory;
        }

        return Paths.get("profile");
    }

    private void deleteProfileImage(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            Files.deleteIfExists(getProfileDirectory().resolve(userId + ".png"));
        } catch (IOException e) {
            throw new RuntimeException("프로필 사진 삭제에 실패했습니다.");
        }
    }

}
