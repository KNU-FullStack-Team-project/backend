package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.AccountDashboardDto;
import org.team12.teamproject.dto.MyAccountBalanceDto;
import org.team12.teamproject.service.AccountService;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:3000"
})
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/my/dashboard")
    public ResponseEntity<AccountDashboardDto> getMyDashboard(
            @RequestParam String email,
            @RequestParam(required = false) Long accountId) {
        if (accountId == null) {
            accountId = accountService.getAccountIdByEmail(email);
        }
        AccountDashboardDto dto = accountService.getDashboard(accountId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/my")
    public ResponseEntity<List<MyAccountBalanceDto>> getMyAccounts(@RequestParam String email) {
        return ResponseEntity.ok(accountService.getMyAccountBalancesByEmail(email));
    }

    @PostMapping("/{accountId}/reset-cash")
    public ResponseEntity<MyAccountBalanceDto> resetCashBalance(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.resetMainAccountBalance(accountId));
    }
}
