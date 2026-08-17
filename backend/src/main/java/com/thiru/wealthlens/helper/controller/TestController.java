package com.thiru.wealthlens.helper.controller;

import com.thiru.wealthlens.helper.dto.helper.ProfitLossDto;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.service.PortfolioService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RequestMapping("/test/user/{email}")
@RestController
public class TestController {
    private final PortfolioService portfolioService;

    @PostMapping("/transact/sell")
    public void getUserTransaction(@PathVariable String email, @RequestBody ProfitLossDto dto) {

        String transactionId = dto.transactionId();
        AssetRequest assetRequest = dto.assetRequest();
        List<AssetEntity> stockEntities = dto.stockEntities();

        portfolioService.updateQuantityBySavingReportAndProfitAndLoss1(UserMail.from(email), transactionId, stockEntities, assetRequest);
    }
}
