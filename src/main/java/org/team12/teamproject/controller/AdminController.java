package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.team12.teamproject.dto.AdminUpdateUserRequestDto;
import org.team12.teamproject.dto.AdminActionLogItemDto;
import org.team12.teamproject.dto.AdminLoginLogItemDto;
import org.team12.teamproject.dto.AdminReportItemDto;
import org.team12.teamproject.dto.UserActivityItemDto;
import org.team12.teamproject.dto.UserProfileResponseDto;
import org.team12.teamproject.service.AdminReportService;
import org.team12.teamproject.service.UserActivityService;
import org.team12.teamproject.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:3000"
})
public class AdminController {

    private final UserService userService;
    private final UserActivityService userActivityService;
    private final AdminReportService adminReportService;

    @GetMapping("/users")
    public ResponseEntity<List<UserProfileResponseDto>> getUsers() {
        return ResponseEntity.ok(userService.getUserList());
    }

    @GetMapping("/users/{userId}/activities")
    public ResponseEntity<List<UserActivityItemDto>> getUserActivities(@PathVariable Long userId) {
        return ResponseEntity.ok(userActivityService.getUserActivities(userId));
    }

    @GetMapping("/login-logs")
    public ResponseEntity<List<AdminLoginLogItemDto>> getLoginLogs() {
        return ResponseEntity.ok(userActivityService.getLoginLogs());
    }

    @GetMapping("/action-logs")
    public ResponseEntity<List<AdminActionLogItemDto>> getActionLogs() {
        return ResponseEntity.ok(userActivityService.getAdminActionLogs());
    }

    @GetMapping("/reports")
    public ResponseEntity<List<AdminReportItemDto>> getReports() {
        return ResponseEntity.ok(adminReportService.getReports());
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<UserProfileResponseDto> updateUser(
            @PathVariable Long userId,
            @RequestBody AdminUpdateUserRequestDto dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(userService.updateAdminUser(userId, dto, authentication.getName()));
    }
}
