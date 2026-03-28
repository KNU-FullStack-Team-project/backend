package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.SignupRequestDto;
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
}