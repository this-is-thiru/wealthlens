package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.TemporaryTransactionService;
import com.thiru.wealthlens.portfolio.service.TradeCollisionRetry;
import com.thiru.wealthlens.shared.dto.RedriveResult;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@MeApi
@RequestMapping("/temporary-transactions")
@RestController
@RequiredArgsConstructor
public class TemporaryTransactionsController {

    private final TradeCollisionRetry tradeCollisionRetry;
    private final TemporaryTransactionService temporaryTransactionService;

    @GetMapping("/all")
    public List<TransactionEntity> getAllTemporaryTransactions(@CallerEmail String email) {
        return temporaryTransactionService.getAllTemporaryTransactions(UserMail.from(email));
    }

    @PostMapping("/redrive")
    public RedriveResult redriveTemporaryTransactions(@CallerEmail String email) {
        return tradeCollisionRetry.redriveTemporaryTransactions(UserMail.from(email));
    }
}
