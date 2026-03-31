package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.LoginRequestDto;
import org.team12.teamproject.dto.SignupRequestDto;
import org.team12.teamproject.dto.UserProfileResponseDto;
import org.team12.teamproject.service.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public String signup(@RequestBody SignupRequestDto dto) {
        return userService.signup(dto);
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequestDto dto) {
        return userService.login(dto);
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponseDto> getProfile(@RequestParam String email) {
        return ResponseEntity.ok(userService.getUserProfile(email));
    }
}
