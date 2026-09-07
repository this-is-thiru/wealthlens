package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.request.ChargeSimulationRequest;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeBreakdownResponse;
import com.thiru.wealthlens.brokercharges.engine.ChargeEngine;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Prices a trade without recording it.
 *
 * <p>What makes this worth a service rather than a controller method is that it writes nothing:
 * it holds the engine and no repository, so a dry run cannot leave a charge behind however the
 * engine changes underneath it. That is asserted, not assumed — {@code ChargeSimulationServiceTest}
 * reads this class's fields and fails if a repository ever appears among them.
 *
 * <p>Not {@code @Transactional}, for the same reason. There is nothing to commit, and declaring a
 * transaction around a read-only path would suggest otherwise to the next reader.
 *
 * <p>Missing input is rejected rather than defaulted. The one that matters is the transaction date:
 * it selects the rate card, so quietly substituting today would price a trade from two years ago
 * against this year's rates and return a confident wrong number.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ChargeSimulationService {

    private final ChargeEngine chargeEngine;

    public ChargeBreakdownResponse simulate(ChargeSimulationRequest request) {
        validate(request);

        ChargeContext context = toContext(request);
        ChargeComputation computation = chargeEngine.compute(context);

        log.debug("Simulated {} {} on {} for {}: {} charged across {} lines",
                request.getEvent(), request.getStockCode(), request.getTransactionDate(),
                request.getBrokerName(), computation.total(), computation.lines().size());

        return ChargeBreakdownResponse.from(computation);
    }

    private static void validate(ChargeSimulationRequest request) {
        if (request == null) {
            throw new BadRequestException("A trade to simulate must be supplied");
        }
        require(request.getBrokerName() != null, "brokerName is required to pick a rate card");
        require(request.getAssetType() != null, "assetType is required to pick a rate card");
        require(request.getEvent() != null, "event is required: a charge applies to a buy or a sell, not to both");
        require(request.getTransactionDate() != null,
                "transactionDate is required: it selects the rate card in force, and defaulting it "
                        + "would price a backfilled trade against today's rates");
        require(request.getQuantity() > 0, "quantity must be greater than zero");
        require(request.getPrice() >= 0, "price must not be negative");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BadRequestException(message);
        }
    }

    private static ChargeContext toContext(ChargeSimulationRequest request) {
        int lotSize = request.getLotSize() > 0 ? request.getLotSize() : 1;

        return new ChargeContext(
                request.getEmail(),
                null,
                request.getOrderId(),
                request.getStockCode(),
                request.getAccountHolder(),
                request.getBrokerName(),
                request.getAssetType(),
                request.getSegment(),
                request.getExchange(),
                request.getPlanCode(),
                request.getEvent(),
                request.getTransactionDate(),
                request.getCorporateActionType(),
                request.getQuantity(),
                request.getPrice(),
                lotSize,
                baseAmounts(request, lotSize),
                List.of(),
                request.getAttributes() == null ? new HashMap<>() : new HashMap<>(request.getAttributes()));
    }

    /**
     * Stated bases win outright. A derivatives trade carries several — premium, notional, intrinsic
     * — and merging a derived turnover into them would hand a rule a base it did not ask for.
     */
    private static Map<AmountBasis, Double> baseAmounts(ChargeSimulationRequest request, int lotSize) {
        if (request.getBaseAmounts() != null && !request.getBaseAmounts().isEmpty()) {
            return new EnumMap<>(request.getBaseAmounts());
        }

        Map<AmountBasis, Double> derived = new EnumMap<>(AmountBasis.class);
        derived.put(AmountBasis.TURNOVER, request.getPrice() * request.getQuantity() * lotSize);
        return derived;
    }
}
