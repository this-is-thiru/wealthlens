package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.request.ChargeSimulationRequest;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeBreakdownResponse;
import com.thiru.wealthlens.brokercharges.engine.ChargeEngine;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.math.BigDecimal;
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
 *
 * <p>The same reasoning governs the FIFO lots. They are optional — a purchase consumes none, and
 * only holding-period charges read them — but a set that does not describe the disposal is rejected
 * rather than priced, because every way of getting them wrong makes the charge smaller rather than
 * making the request fail.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ChargeSimulationService {

    /**
     * A single unit's worth of rounding, at the precision fund units are quoted to.
     *
     * <p>A {@code BigDecimal} rather than a {@code double} for the same reason the engine's
     * arithmetic is: {@code 1e-4} is not exactly representable in binary, so a comparison against it
     * has a boundary no input can land on — untestable, and quietly asymmetric about which side a
     * given pair of quantities falls.
     */
    private static final BigDecimal LOT_QUANTITY_TOLERANCE = new BigDecimal("0.0001");

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
        validateLots(request);
    }

    /**
     * Lots are optional; supplied, they have to describe the disposal they claim to.
     *
     * <p>Every check here exists because the engine's failure mode is silence. A lot with no
     * acquisition date, one acquired after the disposal, or a set that does not add up all produce
     * a smaller charge rather than an error — and a smaller charge from an endpoint whose whole
     * purpose is answering "what will this cost?" is worse than no answer.
     */
    private static void validateLots(ChargeSimulationRequest request) {
        List<LotSlice> lots = request.getLots();
        if (lots == null || lots.isEmpty()) {
            return;
        }

        BigDecimal accounted = BigDecimal.ZERO;
        for (LotSlice lot : lots) {
            require(lot != null, "a lot must not be null");
            require(lot.acquisitionDate() != null,
                    "every lot needs an acquisitionDate: it is what a holding-period charge is measured from");
            require(!lot.acquisitionDate().isAfter(request.getTransactionDate()),
                    "a lot acquired on " + lot.acquisitionDate() + " cannot be disposed of on "
                            + request.getTransactionDate());
            require(lot.quantity() > 0, "every lot needs a quantity greater than zero");
            require(lot.price() >= 0, "a lot price must not be negative");
            accounted = accounted.add(BigDecimal.valueOf(lot.quantity()));
        }

        // Fractional units are the norm for the asset class exit load belongs to, so this compares
        // within a unit's rounding rather than exactly.
        BigDecimal declared = BigDecimal.valueOf(request.getQuantity());
        require(accounted.subtract(declared).abs().compareTo(LOT_QUANTITY_TOLERANCE) <= 0,
                "the lots supplied account for " + trim(accounted) + " units but the trade disposes of "
                        + trim(declared) + "; the difference would be priced at nothing");
    }

    /** Renders a quantity the way the caller wrote it, so the message can be matched to the input. */
    private static String trim(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
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
                request.getLots() == null ? List.of() : List.copyOf(request.getLots()),
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
