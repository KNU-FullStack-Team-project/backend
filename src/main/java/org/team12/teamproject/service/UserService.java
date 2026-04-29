package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.TimeUnit;
import org.team12.teamproject.dto.AdminUpdateUserRequestDto;
import org.team12.teamproject.dto.ChangeNicknameRequestDto;
import org.team12.teamproject.dto.ChangePasswordRequestDto;
import org.team12.teamproject.dto.GoogleLoginRequestDto;
import org.team12.teamproject.dto.GoogleSignupRequestDto;
import org.team12.teamproject.dto.LoginRequestDto;
import org.team12.teamproject.dto.LoginResponseDto;
import org.team12.teamproject.dto.ResetPasswordRequestDto;
import org.team12.teamproject.dto.SignupRequestDto;
import org.team12.teamproject.dto.SocialLoginResultDto;
import org.team12.teamproject.dto.UserProfileResponseDto;
import org.team12.teamproject.dto.WithdrawUserRequestDto;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.exception.LoginFailedException;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.repository.UserRepository;
import org.team12.teamproject.security.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.URL;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int CAPTCHA_THRESHOLD = 5;
    private static final String SOCIAL_GOOGLE_PASSWORD_MARKER = "{social}google";
    private static final int MAX_NICKNAME_LENGTH = 12;
    private static final String NICKNAME_PATTERN = "^[A-Za-z0-9가-힣]{1,12}$";
    private static final String NICKNAME_RULE_MESSAGE = "닉네임은 12자 이하의 한글, 영문, 숫자만 사용할 수 있습니다.";
    private static final DateTimeFormatter SUSPENSION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final RecaptchaService recaptchaService;
    private final UserActivityAuditLogger userActivityAuditLogger;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    private final Map<String, Integer> loginFailureCounts = new ConcurrentHashMap<>();
    private static final String RT_KEY_PREFIX = "RT:";
    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 7;

    @Transactional
    public String signup(SignupRequestDto dto) {
        String email = dto.getEmail() != null ? dto.getEmail().trim() : "";
        String nickname = normalizeNickname(dto.getNickname());
        String nicknameValidationMessage = validateNickname(nickname);
        if (nicknameValidationMessage != null) return nicknameValidationMessage;
        if (email.isEmpty()) return "이메일을 입력해주세요.";

        if (!emailService.isVerified(email)) {
            return "이메일 인증을 먼저 완료해주세요.";
        }

        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (isEmailInUseByActiveUser(email)) {
            return "이미 사용 중인 이메일입니다.";
        }

        if (existingUser != null && "QUIT".equalsIgnoreCase(existingUser.getStatus())) {
            if (!nickname.equals(existingUser.getNickname())
                    && userRepository.countByNickname(nickname) > 0) {
                return "이미 사용 중인 닉네임입니다.";
            }

            resetUserAssets(existingUser.getId());

            existingUser.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
            existingUser.setNickname(nickname);
            existingUser.setRole("USER");
            existingUser.setStatus("REJOINED");
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

            return "재가입 완료";
        }

        if (userRepository.countByNickname(nickname) > 0) {
            return "이미 사용 중인 닉네임입니다.";
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        User user = User.builder()
                .email(email)
                .passwordHash(encodedPassword)
                .nickname(nickname)
                .profileImageUrl(null)
                .role("USER")
                .status("ACTIVE")
                .marketingConsent(dto.getMarketingConsent() != null && dto.getMarketingConsent())
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        createDefaultAccount(user);
        emailService.clearVerification(dto.getEmail());

        return "회원가입 완료";
    }

    private void createDefaultAccount(User user) {
        Account account = Account.builder()
                .user(user)
                .accountType("MAIN")
                .accountName(user.getNickname() + "의 기본 계좌")
                .cashBalance(new BigDecimal("5000000"))
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
        String normalizedEmail = normalizeEmail(email);

        if (isCaptchaRequired(normalizedEmail)) {
            validateCaptcha(dto.getCaptchaToken());
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> buildLoginFailedException(normalizedEmail));

        releaseExpiredSuspension(user);

        if (!matchesPassword(dto.getPassword(), user.getPasswordHash())) {
            throw buildLoginFailedException(normalizedEmail);
        }

        if ("QUIT".equalsIgnoreCase(user.getStatus())) {
            throw buildLoginFailedException(normalizedEmail);
        }

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            throw new LoginFailedException(buildSuspensionMessage(user), isCaptchaRequired(normalizedEmail));
        }

        if (!isLoginAllowedStatus(user.getStatus())) {
            throw new LoginFailedException("비활성화된 계정입니다.", isCaptchaRequired(normalizedEmail));
        }

        clearLoginFailures(normalizedEmail);

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        // Refresh Token Redis 저장
        redisTemplate.opsForValue().set(
                RT_KEY_PREFIX + user.getEmail(),
                refreshToken,
                REFRESH_TOKEN_VALIDITY_DAYS,
                TimeUnit.DAYS
        );

        List<Account> accounts = accountRepository.findByUserId(user.getId());
        Long accountId = accounts.isEmpty() ? null : accounts.get(0).getId();

        userActivityAuditLogger.log(
                user.getId(),
                user.getEmail(),
                "LOGIN",
                "USER",
                String.valueOf(user.getId()),
                "role=" + user.getRole()
        );

        return new LoginResponseDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                normalizeProfileImageUrl(user.getProfileImageUrl()),
                user.getRole(),
                "로그인 성공",
                accessToken,
                refreshToken,
                accountId,
                false,
                isSocialPasswordMarker(user.getPasswordHash())
        );
    }

    @Transactional
    public SocialLoginResultDto loginWithGoogle(GoogleLoginRequestDto dto) {
        GoogleIdToken.Payload payload = googleTokenVerifierService.verify(dto.getCredential());

        String email = payload.getEmail();
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Google account email is missing.");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            String name = payload.get("name") instanceof String ? (String) payload.get("name") : null;
            return new SocialLoginResultDto(
                    true,
                    null,
                    email,
                    generateAvailableNickname(name, email),
                    extractGooglePicture(payload),
                    false
            );
        }

        releaseExpiredSuspension(user);

        if ("QUIT".equalsIgnoreCase(user.getStatus())) {
            return new SocialLoginResultDto(
                    true,
                    null,
                    email,
                    "",
                    extractGooglePicture(payload),
                    true
            );
        }

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException(buildSuspensionMessage(user));
        }

        if (!isLoginAllowedStatus(user.getStatus())) {
            throw new RuntimeException("비활성화된 계정입니다.");
        }

        clearLoginFailures(normalizeEmail(email));
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        String pictureUrl = extractGooglePicture(payload);
        if (pictureUrl != null && !pictureUrl.isBlank()) {
            saveSocialProfileImage(user, pictureUrl);
        }

        userRepository.save(user);

        return new SocialLoginResultDto(
                false,
                buildLoginResponse(user, "간편로그인 성공"),
                null,
                null,
                null,
                false
        );
    }

    @Transactional
    public LoginResponseDto signupWithGoogle(GoogleSignupRequestDto dto) {
        GoogleIdToken.Payload payload = googleTokenVerifierService.verify(dto.getCredential());

        String email = payload.getEmail();
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Google account email is missing.");
        }

        String nickname = normalizeNickname(dto.getNickname());
        validateNicknameOrThrow(nickname);

        User existingUser = userRepository.findByEmail(email).orElse(null);
        if (existingUser != null && !"QUIT".equalsIgnoreCase(existingUser.getStatus())) {
            throw new RuntimeException("이미 가입된 계정입니다. 간편로그인을 다시 시도해 주세요.");
        }

        if (userRepository.countByNickname(nickname) > 0) {
            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
        }

        if (existingUser != null) {
            resetUserAssets(existingUser.getId());

            existingUser.setPasswordHash(SOCIAL_GOOGLE_PASSWORD_MARKER);
            existingUser.setNickname(nickname);
            existingUser.setRole("USER");
            existingUser.setStatus("REJOINED");
            existingUser.setMarketingConsent(dto.getMarketingConsent() != null && dto.getMarketingConsent());
            existingUser.setEmailVerified(true);
            existingUser.setWithdrawnAt(null);
            existingUser.setSuspendedAt(null);
            existingUser.setDeletedAt(null);
            existingUser.setCreatedAt(LocalDateTime.now());
            existingUser.setUpdatedAt(LocalDateTime.now());
            saveSocialProfileImage(existingUser, extractGooglePicture(payload));
            userRepository.save(existingUser);
            createDefaultAccount(existingUser);

            return buildLoginResponse(existingUser, "다시 돌아오신 것을 환영합니다.");
        }

        User user = createGoogleUser(
                payload,
                nickname,
                dto.getMarketingConsent() != null && dto.getMarketingConsent()
        );
        return buildLoginResponse(user, "간편회원가입 및 로그인 성공");
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
        String cleanNickname = normalizeNickname(nickname);
        String cleanEmail = email != null ? email.trim() : "";
        String nicknameValidationMessage = validateNickname(cleanNickname);
        if (nicknameValidationMessage != null) return nicknameValidationMessage;

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

            user.setProfileImageUrl("/api/profile/" + user.getId() + ".png");
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
        String nickname = normalizeNickname(dto.getNickname());
        validateNicknameOrThrow(nickname);

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

        String previousNickname = user.getNickname();
        user.setNickname(nickname);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        if (!nickname.equals(previousNickname)) {
            userActivityAuditLogger.log(
                    user.getId(),
                    user.getEmail(),
                    "PROFILE_NICKNAME_UPDATE",
                    "USER",
                    String.valueOf(user.getId()),
                    "before=" + previousNickname + ", after=" + nickname
            );
        }

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

    @Transactional
    public List<UserProfileResponseDto> getUserList() {
        return userRepository.findAll().stream()
                .peek(this::releaseExpiredSuspension)
                .map(this::toUserProfile)
                .toList();
    }

    @Transactional
    public UserProfileResponseDto updateAdminUser(Long userId, AdminUpdateUserRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        releaseExpiredSuspension(user);
        String previousStatus = user.getStatus();

        if (!"USER".equals(dto.getRole()) && !"ADMIN".equals(dto.getRole())) {
            throw new RuntimeException("권한 값이 올바르지 않습니다.");
        }

        if (!"ACTIVE".equals(dto.getStatus())
                && !"SUSPENDED".equals(dto.getStatus())
                && !"REJOINED".equals(dto.getStatus())) {
            throw new RuntimeException("상태 값이 올바르지 않습니다.");
        }

        user.setRole(dto.getRole());
        applyUserStatus(user, dto);
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        logSuspensionChange(savedUser, previousStatus, dto);
        return toUserProfile(savedUser);
    }

    public String withdraw(WithdrawUserRequestDto dto) {
        String email = dto.getEmail() != null ? dto.getEmail().trim() : "";
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("회원정보가 일치하지 않습니다."));

        deleteProfileImage(user.getId());

        user.setProfileImageUrl(null);
        user.setNickname(generateWithdrawnNickname(user));
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

        if (isSocialPasswordMarker(storedPassword)) {
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

    private String normalizeNickname(String nickname) {
        return nickname != null ? nickname.trim() : "";
    }

    private String validateNickname(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return "닉네임을 입력해주세요.";
        }

        if (nickname.length() > MAX_NICKNAME_LENGTH || !nickname.matches(NICKNAME_PATTERN)) {
            return NICKNAME_RULE_MESSAGE;
        }

        return null;
    }

    private void validateNicknameOrThrow(String nickname) {
        String validationMessage = validateNickname(nickname);
        if (validationMessage != null) {
            throw new RuntimeException(validationMessage);
        }
    }

    private LoginFailedException buildLoginFailedException(String normalizedEmail) {
        int failCount = incrementLoginFailures(normalizedEmail);
        boolean captchaRequired = failCount >= CAPTCHA_THRESHOLD;

        if (captchaRequired) {
            return new LoginFailedException("로그인 5회 이상 실패하여 reCAPTCHA 인증이 필요합니다.", true);
        }

        return new LoginFailedException("회원정보가 일치하지 않습니다.", false);
    }

    private void validateCaptcha(String captchaToken) {
        if (captchaToken == null || captchaToken.isBlank()) {
            throw new LoginFailedException("로그인 5회 이상 실패하여 reCAPTCHA 인증이 필요합니다.", true);
        }

        if (!recaptchaService.verify(captchaToken)) {
            throw new LoginFailedException("reCAPTCHA 인증에 실패했습니다. 다시 시도해 주세요.", true);
        }
    }

    private int incrementLoginFailures(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return CAPTCHA_THRESHOLD;
        }

        return loginFailureCounts.merge(normalizedEmail, 1, Integer::sum);
    }

    private void clearLoginFailures(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return;
        }

        loginFailureCounts.remove(normalizedEmail);
    }

    private boolean isCaptchaRequired(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }

        return loginFailureCounts.getOrDefault(normalizedEmail, 0) >= CAPTCHA_THRESHOLD;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private boolean isLoginAllowedStatus(String status) {
        return "ACTIVE".equalsIgnoreCase(status) || "REJOINED".equalsIgnoreCase(status);
    }

    private String normalizeProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl != null && profileImageUrl.startsWith("/profile")) {
            return "/api" + profileImageUrl;
        }

        return profileImageUrl;
    }

    private boolean isSocialPasswordMarker(String storedPassword) {
        return SOCIAL_GOOGLE_PASSWORD_MARKER.equals(storedPassword);
    }

    private String generateWithdrawnNickname(User user) {
        String base = "withdrawn_" + (user.getId() != null ? user.getId() : "user");
        String candidate = base;
        int suffix = 1;

        while (userRepository.countByNickname(candidate) > 0) {
            candidate = base + "_" + suffix;
            suffix++;
        }

        return candidate;
    }

    private void saveSocialProfileImage(User user, String imageUrl) {
        if (user == null || user.getId() == null || imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        try {
            Path profileDirectory = getProfileDirectory();
            Files.createDirectories(profileDirectory);

            try (InputStream inputStream = new URL(imageUrl).openStream()) {
                BufferedImage profileImage = ImageIO.read(inputStream);
                if (profileImage == null) {
                    return;
                }

                Path profileImagePath = profileDirectory.resolve(user.getId() + ".png");
                ImageIO.write(profileImage, "png", profileImagePath.toFile());
                user.setProfileImageUrl("/profile/" + user.getId() + ".png");
                user.setUpdatedAt(LocalDateTime.now());
            }
        } catch (IOException e) {
            // Keep social login working even if the remote profile image cannot be downloaded.
        }
    }

    private UserProfileResponseDto toUserProfile(User user) {
        List<Account> accounts = accountRepository.findByUserId(user.getId());

        if (accounts.isEmpty()) {
            createDefaultAccount(user);
            accounts = accountRepository.findByUserId(user.getId());
        }

        Long accountId = accounts.get(0).getId();

        String finalProfileImageUrl = user.getProfileImageUrl();
        if (finalProfileImageUrl != null && finalProfileImageUrl.startsWith("/profile")) {
            finalProfileImageUrl = "/api" + finalProfileImageUrl;
        }

        return UserProfileResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(finalProfileImageUrl)
                .socialLogin(isSocialPasswordMarker(user.getPasswordHash()))
                .role(user.getRole())
                .status(user.getStatus())
                .suspendedUntil(user.getSuspendedUntil() != null ? user.getSuspendedUntil().toString() : null)
                .suspensionReason(user.getSuspensionReason())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .accountId(accountId)
                .accountCount(accounts.size())
                .build();
    }

    private void applyUserStatus(User user, AdminUpdateUserRequestDto dto) {
        if (!"SUSPENDED".equals(dto.getStatus())) {
            user.setStatus(dto.getStatus());
            user.setSuspendedAt(null);
            user.setSuspendedUntil(null);
            user.setSuspensionReason(null);
            return;
        }

        boolean newlySuspended = !"SUSPENDED".equalsIgnoreCase(user.getStatus());
        Double suspensionHours = dto.getSuspensionHours();

        if (newlySuspended && suspensionHours == null) {
            throw new RuntimeException("정지 기간을 시간 단위로 입력해주세요.");
        }

        user.setStatus("SUSPENDED");

        if (suspensionHours == null) {
            return;
        }

        if (!suspensionHours.equals(-1.0) && suspensionHours <= 0) {
            throw new RuntimeException("정지 기간은 -1 또는 0이 아닌 양수 시간으로 입력해주세요.");
        }

        String reason = dto.getSuspensionReason() != null ? dto.getSuspensionReason().trim() : "";
        if (reason.isBlank()) {
            throw new RuntimeException("정지 사유를 입력해주세요.");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setSuspendedAt(now);
        user.setSuspendedUntil(suspensionHours.equals(-1.0)
                ? null
                : now.plus(Duration.ofSeconds(Math.max(1, Math.round(suspensionHours * 3600)))));
        user.setSuspensionReason(reason);
    }

    private void releaseExpiredSuspension(User user) {
        if (!"SUSPENDED".equalsIgnoreCase(user.getStatus())) {
            return;
        }
        if (user.getSuspendedUntil() == null || user.getSuspendedUntil().isAfter(LocalDateTime.now())) {
            return;
        }

        user.setStatus("ACTIVE");
        user.setSuspendedAt(null);
        user.setSuspendedUntil(null);
        user.setSuspensionReason(null);
        user.setUpdatedAt(LocalDateTime.now());
        userActivityAuditLogger.log(
                user.getId(),
                user.getEmail(),
                "SUSPENSION_RELEASE",
                "USER",
                String.valueOf(user.getId()),
                "type=AUTO"
        );
    }

    private String buildSuspensionMessage(User user) {
        String reason = user.getSuspensionReason() != null && !user.getSuspensionReason().isBlank()
                ? user.getSuspensionReason()
                : "관리자에 의해 정지되었습니다.";
        String releaseTime = user.getSuspendedUntil() != null
                ? user.getSuspendedUntil().format(SUSPENSION_TIME_FORMAT)
                : "영구 정지";
        return "정지된 계정입니다. 사유: " + reason + " / 해제시간: " + releaseTime;
    }

    private void logSuspensionChange(User user, String previousStatus, AdminUpdateUserRequestDto dto) {
        if ("SUSPENDED".equals(dto.getStatus())) {
            String releaseTime = user.getSuspendedUntil() != null
                    ? user.getSuspendedUntil().format(SUSPENSION_TIME_FORMAT)
                    : "PERMANENT";
            userActivityAuditLogger.log(
                    user.getId(),
                    user.getEmail(),
                    "SUSPENSION_SET",
                    "USER",
                    String.valueOf(user.getId()),
                    "hours=" + dto.getSuspensionHours()
                            + "; until=" + releaseTime
                            + "; reason=" + user.getSuspensionReason()
            );
            return;
        }

        if ("SUSPENDED".equalsIgnoreCase(previousStatus) && !"SUSPENDED".equals(dto.getStatus())) {
            userActivityAuditLogger.log(
                    user.getId(),
                    user.getEmail(),
                    "SUSPENSION_RELEASE",
                    "USER",
                    String.valueOf(user.getId()),
                    "type=MANUAL; nextStatus=" + dto.getStatus()
            );
        }
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

    public String refreshToken(String refreshToken) {
        if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 리프레시 토큰입니다.");
        }
        String email = jwtUtil.getEmailFromToken(refreshToken);
        String savedToken = redisTemplate.opsForValue().get(RT_KEY_PREFIX + email);
        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new RuntimeException("만료되었거나 유효하지 않은 세션입니다.");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        return jwtUtil.generateAccessToken(user.getEmail(), user.getRole());
    }
    public void logout(String email) {
        if (email != null) {
            redisTemplate.delete(RT_KEY_PREFIX + email);
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        userActivityAuditLogger.log(
                user.getId(),
                user.getEmail(),
                "LOGOUT",
                "USER",
                String.valueOf(user.getId()),
                "client_logout"
        );
    }

    private User createGoogleUser(GoogleIdToken.Payload payload, String nickname, boolean marketingConsent) {
        String email = payload.getEmail().trim();
        String pictureUrl = extractGooglePicture(payload);

        User user = User.builder()
                .email(email)
                .passwordHash(SOCIAL_GOOGLE_PASSWORD_MARKER)
                .nickname(nickname)
                .profileImageUrl(null)
                .role("USER")
                .status("ACTIVE")
                .marketingConsent(marketingConsent)
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        saveSocialProfileImage(user, pictureUrl);
        createDefaultAccount(user);
        return user;
    }

    private String extractGooglePicture(GoogleIdToken.Payload payload) {
        return payload.get("picture") instanceof String ? (String) payload.get("picture") : null;
    }

    private String generateAvailableNickname(String name, String email) {
        String seed = name != null && !name.isBlank() ? name : email.substring(0, email.indexOf('@'));
        String base = seed.replaceAll("[^\\p{L}\\p{N}_]", "").trim();

        if (base.isBlank()) {
            base = "user";
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.countByNickname(candidate) > 0) {
            candidate = base + suffix;
            suffix++;
        }

        return candidate;
    }

    private LoginResponseDto buildLoginResponse(User user, String message) {
        List<Account> accounts = accountRepository.findByUserId(user.getId());
        Long accountId = accounts.isEmpty() ? null : accounts.get(0).getId();
        
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        // Refresh Token Redis 저장
        redisTemplate.opsForValue().set(
                RT_KEY_PREFIX + user.getEmail(),
                refreshToken,
                REFRESH_TOKEN_VALIDITY_DAYS,
                TimeUnit.DAYS
        );

        userActivityAuditLogger.log(
                user.getId(),
                user.getEmail(),
                "LOGIN",
                "USER",
                String.valueOf(user.getId()),
                "role=" + user.getRole()
        );

        return new LoginResponseDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                normalizeProfileImageUrl(user.getProfileImageUrl()),
                user.getRole(),
                message,
                accessToken,
                refreshToken,
                accountId,
                false,
                isSocialPasswordMarker(user.getPasswordHash())
        );
    }
}
