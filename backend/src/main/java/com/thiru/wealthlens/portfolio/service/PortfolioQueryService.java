package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortfolioQueryService {

    private final PortfolioRepository portfolioRepository;

    public List<AssetEntity> findAssetsForCorporateAction(String email, String stockCode, BrokerName brokerName, String accountHolder, LocalDate transactionDate) {
        return portfolioRepository.findByEmailAndStockCodeAndBrokerNameAndAccountHolderAndTransactionDate(
                email, stockCode, brokerName, accountHolder, transactionDate)
                .map(List::of)
                .orElseGet(List::of);
    }
}
