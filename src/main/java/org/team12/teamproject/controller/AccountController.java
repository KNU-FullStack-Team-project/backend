package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.AccountDashboardDto;
import org.team12.teamproject.service.AccountService;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/my/dashboard")
    public ResponseEntity<AccountDashboardDto> getMyDashboard() {
        Long accountId = accountService.getFallbackAccountId();
        AccountDashboardDto dto = accountService.getDashboard(accountId);
        return ResponseEntity.ok(dto);
    }
}
