package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.TransactionResponse;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.shared.dto.BulkGetRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.web.CallerEmail;
import com.thiru.wealthlens.shared.web.MeApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@MeApi
@RequestMapping("/transactions")
@RestController
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping("/search")
    public PagedModel<TransactionResponse> getUserTransaction(@CallerEmail String email, @Valid @RequestBody BulkGetRequest bulkGetRequest,
            Pageable pageable) {
        return new PagedModel<>(transactionService.userTransactions(UserMail.from(email), bulkGetRequest.getQueryFilters(), pageable));
    }

    @GetMapping
    public PagedModel<TransactionResponse> getUserTransaction(@CallerEmail String email, Pageable pageable) {
        return new PagedModel<>(transactionService.getAllUserTransactions(UserMail.from(email), pageable));
    }
}
