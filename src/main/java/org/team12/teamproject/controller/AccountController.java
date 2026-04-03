package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.AccountDashboardDto;
import org.team12.teamproject.dto.AccountDepositRequestDto;
import org.team12.teamproject.dto.MyAccountBalanceDto;
import org.team12.teamproject.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/my/dashboard")
    public ResponseEntity<AccountDashboardDto> getMyDashboard(@RequestParam String email) {
        Long accountId = accountService.getAccountIdByEmail(email);
        AccountDashboardDto dto = accountService.getDashboard(accountId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/my")
    public ResponseEntity<List<MyAccountBalanceDto>> getMyAccounts(@RequestParam String email) {
        return ResponseEntity.ok(accountService.getMyAccountBalancesByEmail(email));
    }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<MyAccountBalanceDto> depositToAccount(
            @PathVariable Long accountId,
            @RequestBody AccountDepositRequestDto dto
    ) {
        return ResponseEntity.ok(accountService.depositToAccount(accountId, dto));
    }
}
