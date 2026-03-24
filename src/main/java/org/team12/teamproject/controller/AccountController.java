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

    // 로그인 기능이 없으므로 임시로 fallbackAccountId (아마도 1)을 사용해 대시보드를 내려받도록 합니다.
    @GetMapping("/my/dashboard")
    public ResponseEntity<AccountDashboardDto> getMyDashboard() {
        Long accountId = accountService.getFallbackAccountId();
        AccountDashboardDto dto = accountService.getDashboard(accountId);
        return ResponseEntity.ok(dto);
    }
}
