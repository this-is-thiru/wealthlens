package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.service.PortfolioQueryService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.web.AdminApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One user's holdings, read by the owner: the check after an assets rebuild (runbook §2.12 in tag docs-history-m2, D-855 C3a). A user reads their own at
 * {@code /api/v1/me/portfolio/stocks}.
 */
@RequiredArgsConstructor
@AdminApi
@RequestMapping("/users/{email}")
@RestController
public class HoldingsAdminController {

    private final PortfolioQueryService portfolioQueryService;

    @PreAuthorize("hasRole('SUPER_USER')")
    @GetMapping("/stocks")
    public List<AssetResponse> stocks(@PathVariable String email) {
        return portfolioQueryService.getAllStocks(UserMail.from(email));
    }

    @PreAuthorize("hasRole('SUPER_USER')")
    @GetMapping("/mutual-funds")
    public List<AssetResponse> mutualFunds(@PathVariable String email) {
        return portfolioQueryService.getMutualFunds(UserMail.from(email));
    }
}
