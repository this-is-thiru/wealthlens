package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.amount;
import static com.thiru.wealthlens.brokercharges.engine.ChargeAmounts.requireNonNegative;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.DedupeScope;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * A fixed amount levied at most once per window.
 *
 * <p>Depository charges are the motivating case: one debit per scrip per day per demat account,
 * however many sell transactions make it up.
 *
 * <h2>The account holder is part of every key</h2>
 * A depository charge is levied per demat account. A user tracking holdings for two people who each
 * sell the same scrip on the same day incurs two separate debits and therefore two charges — keying
 * without the account holder records one, and undercharges. That was a live defect in the superseded
 * implementation (D10), fixed there ahead of this engine.
 *
 * <p>The lookup is never absorbed. A failed query read as "nothing charged yet" would double-charge
 * silently, which is worse than the computation failing.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ScopedFlatChargeCalculator implements ChargeCalculator {

    private final UserChargeRepository userChargeRepository;

    @Override
    public ChargeBasis basis() {
        return ChargeBasis.SCOPED_FLAT;
    }

    @Override
    public BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
        BigDecimal flatAmount = requireNonNegative(rule, "flat amount", amount(rule.getFlatAmount()));
        if (flatAmount.signum() == 0) {
            // Nothing to levy, so nothing to deduplicate, so no reason to touch the database.
            return BigDecimal.ZERO;
        }

        DedupeScope scope = rule.getDedupeScope();
        if (scope == null || scope == DedupeScope.NONE) {
            return flatAmount;
        }

        return alreadyLevied(scope, rule, context) ? BigDecimal.ZERO : flatAmount;
    }

    private boolean alreadyLevied(DedupeScope scope, ChargeRule rule, ChargeContext context) {
        return switch (scope) {
            case NONE -> false;
            case PER_SCRIP_PER_DAY -> userChargeRepository.existsChargeForScripOnDate(
                    context.email(), context.accountHolder(), context.brokerName(),
                    context.stockCode(), context.transactionDate(), rule.getCode());
            case PER_DAY -> userChargeRepository.existsChargeForDay(
                    context.email(), context.accountHolder(), context.brokerName(),
                    context.transactionDate(), rule.getCode());
            case PER_ORDER -> perOrder(rule, context);
        };
    }

    /**
     * Deduplicating against a null order would suppress the charge on every trade after the first,
     * across unrelated orders. Charging is the safer side of that mistake and the visible one.
     */
    private boolean perOrder(ChargeRule rule, ChargeContext context) {
        if (context.orderId() == null || context.orderId().isBlank()) {
            log.warn("Charge rule {} is scoped per order but transaction {} carries no order id; "
                    + "levying it rather than deduplicating on nothing",
                    rule.getCode(), context.transactionId());
            return false;
        }
        return userChargeRepository.existsChargeForOrder(
                context.email(), context.accountHolder(), context.orderId(), rule.getCode());
    }
}
