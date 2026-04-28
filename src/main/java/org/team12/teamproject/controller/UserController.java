package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.team12.teamproject.dto.ChangeNicknameRequestDto;
import org.team12.teamproject.dto.ChangePasswordRequestDto;
import org.team12.teamproject.dto.GoogleLoginRequestDto;
import org.team12.teamproject.dto.GoogleSignupRequestDto;
import org.team12.teamproject.dto.LoginRequestDto;
import org.team12.teamproject.dto.LoginResponseDto;
import org.team12.teamproject.dto.ResetPasswordRequestDto;
import org.team12.teamproject.dto.SocialLoginResultDto;
import org.team12.teamproject.dto.SignupRequestDto;
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

    @org.springframework.beans.factory.annotation.Value("${cookie.secure}")
    private boolean cookieSecure;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequestDto dto) {
        return ResponseEntity.ok(userService.signup(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequestDto, HttpServletResponse response) {
        try {
            LoginResponseDto loginResponse = userService.login(loginRequestDto);
            setTokenCookies(response, loginResponse);
            return ResponseEntity.ok(loginResponse);
        } catch (LoginFailedException e) {
            LoginResponseDto errorResponse = new LoginResponseDto(
                    null, null, null, null, null, e.getMessage(), null, null, null, e.isCaptchaRequired(), false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    @PostMapping("/social/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequestDto dto, HttpServletResponse response) {
        try {
            Object result = userService.loginWithGoogle(dto);
            if (result instanceof SocialLoginResultDto) {
                SocialLoginResultDto socialResult = (SocialLoginResultDto) result;
                if (!socialResult.isSignupRequired() && socialResult.getLogin() != null) {
                    setTokenCookies(response, socialResult.getLogin());
                }
            }
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/social/google/signup")
    public ResponseEntity<?> googleSignup(@RequestBody GoogleSignupRequestDto dto, HttpServletResponse response) {
        try {
            LoginResponseDto loginResponse = userService.signupWithGoogle(dto);
            setTokenCookies(response, loginResponse);
            return ResponseEntity.ok(loginResponse);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            String refreshToken = null;
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("refreshToken".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                    }
                }
            }

            if (refreshToken == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Refresh token missing"));
            }

            String newAccessToken = userService.refreshToken(refreshToken);
            
            // 새 Access Token 쿠키 설정
            Cookie accessTokenCookie = new Cookie("accessToken", newAccessToken);
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setSecure(cookieSecure);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge(3600); // 1시간
            response.addCookie(accessTokenCookie);

            return ResponseEntity.ok(Map.of("token", newAccessToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "Refresh failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal principal, HttpServletResponse response) {
        if (principal != null) {
            userService.logout(principal.getName());
        }
        
        // 쿠키 삭제
        clearTokenCookies(response);
        
        return ResponseEntity.ok().build();
    }

    private void setTokenCookies(HttpServletResponse response, LoginResponseDto loginResponse) {
        if (loginResponse.getToken() != null) {
            Cookie accessTokenCookie = new Cookie("accessToken", loginResponse.getToken());
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setSecure(cookieSecure);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge(3600); // 1시간
            response.addCookie(accessTokenCookie);
        }

        if (loginResponse.getRefreshToken() != null) {
            Cookie refreshTokenCookie = new Cookie("refreshToken", loginResponse.getRefreshToken());
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setSecure(cookieSecure);
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge(7 * 24 * 3600); // 7일
            response.addCookie(refreshTokenCookie);
        }
    }

    private void clearTokenCookies(HttpServletResponse response) {
        Cookie accessTokenCookie = new Cookie("accessToken", null);
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(0);
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refreshToken", null);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);
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
