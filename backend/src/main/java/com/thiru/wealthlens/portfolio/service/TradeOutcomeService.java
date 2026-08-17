package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class TradeOutcomeService {

    private final TradeOutcomeRepository tradeOutcomeRepository;

    public void saveTradeOutcome(TradeOutcomeContext context) {
        TradeOutcomeEntity entity = TJsonMapper.copy(context, TradeOutcomeEntity.class);
        tradeOutcomeRepository.save(entity);
        log.info("Saved trade outcome for email: {}, stockCode: {}", context.getEmail(), context.getStockCode());
    }

    public void saveTradeOutcome(UserMail userMail, TradeOutcomeContext context) {
        TradeOutcomeEntity entity = TJsonMapper.copy(context, TradeOutcomeEntity.class);
        entity.setEmail(userMail.getEmail());
        tradeOutcomeRepository.save(entity);
        log.info("Saved trade outcome for email: {}, stockCode: {}", userMail.getEmail(), context.getStockCode());
    }

    public List<TradeOutcomeEntity> getTradeOutcomesByEmail(String email) {
        return tradeOutcomeRepository.findByEmail(email);
    }

    public List<TradeOutcomeEntity> getTradeOutcomesByEmail(UserMail userMail) {
        return tradeOutcomeRepository.findByEmail(userMail.getEmail());
    }

    public void deleteByEmail(String email) {
        tradeOutcomeRepository.deleteByEmail(email);
        log.info("Deleted all trade outcomes for email: {}", email);
    }

    public void deleteByEmail(UserMail userMail) {
        tradeOutcomeRepository.deleteByEmail(userMail.getEmail());
        log.info("Deleted all trade outcomes for email: {}", userMail.getEmail());
    }
}
