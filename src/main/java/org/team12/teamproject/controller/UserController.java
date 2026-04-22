package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
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
import org.team12.teamproject.exception.LoginFailedException;
import org.team12.teamproject.service.UserService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:3000"
})
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequestDto dto) {
        return ResponseEntity.ok(userService.signup(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequestDto) {
        try {
            LoginResponseDto response = userService.login(loginRequestDto);
            return ResponseEntity.ok(response);
        } catch (LoginFailedException e) {
            LoginResponseDto response = new LoginResponseDto(
                    null,
                    null,
                    null,
                    null,
                    null,
                    e.getMessage(),
                    null,
                    null,
                    e.isCaptchaRequired(),
                    false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/social/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequestDto dto) {
        try {
            return ResponseEntity.ok(userService.loginWithGoogle(dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/social/google/signup")
    public ResponseEntity<?> googleSignup(@RequestBody GoogleSignupRequestDto dto) {
        try {
            LoginResponseDto response = userService.signupWithGoogle(dto);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }
            String newToken = userService.refreshToken(email);
            return ResponseEntity.ok(Map.of("token", newToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "Refresh failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal principal) {
        userService.logout(principal.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check-email")
    public ResponseEntity<String> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(userService.checkEmail(email));
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<String> checkNickname(@RequestParam String nickname,
            @RequestParam(required = false) String email) {
        return ResponseEntity.ok(userService.checkNickname(nickname, email));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> getProfile(@RequestParam String email) {
        return ResponseEntity.ok(userService.getUserProfile(email));
    }

    @PostMapping("/profile-image")
    public ResponseEntity<UserProfileResponseDto> updateProfileImage(
            @RequestParam String email,
            @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(userService.updateProfileImage(email, image));
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequestDto dto) {
        return ResponseEntity.ok(userService.changePassword(dto));
    }

    @PostMapping("/change-nickname")
    public ResponseEntity<UserProfileResponseDto> changeNickname(@RequestBody ChangeNicknameRequestDto dto) {
        return ResponseEntity.ok(userService.changeNickname(dto));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequestDto dto) {
        return ResponseEntity.ok(userService.resetPassword(dto));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<String> withdraw(@RequestBody WithdrawUserRequestDto dto) {
        return ResponseEntity.ok(userService.withdraw(dto));
    }
}
