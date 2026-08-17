package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.PortfolioService;
import com.thiru.wealthlens.portfolio.service.TemporaryTransactionService;
import com.thiru.wealthlens.shared.dto.RedriveResult;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/temporary-transactions/user/{email}")
public class TemporaryTransactionsController {

    private final PortfolioService portfolioService;
    private final TemporaryTransactionService temporaryTransactionService;

    @GetMapping("/all")
    public List<TransactionEntity> getAllTemporaryTransactions(@PathVariable String email) {
        return temporaryTransactionService.getAllTemporaryTransactions(UserMail.from(email));
    }

    @PostMapping("/redrive")
    public RedriveResult redriveTemporaryTransactions(@PathVariable String email) {
        return portfolioService.redriveTemporaryTransactions(UserMail.from(email));
    }
}
