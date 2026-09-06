package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Finds the rate card that priced a trade.
 *
 * <h2>Validity is the repository's job; choosing is this class's</h2>
 * The date window and the status predicate live in {@code findCandidates}, which returns every card
 * that could apply — including superseded ones, because a transaction backdated into a closed
 * window must still be priced by the card in force then (ADR-12). What arrives here is a list of
 * cards that are all already valid.
 *
 * <h2>Specificity, and why a conflict disqualifies</h2>
 * A dimension a card leaves null matches anything; a dimension it declares must agree. A card that
 * disagrees is removed from consideration rather than merely outranked, because outranking is not
 * enough — with no better candidate, an intraday card would go on to price a delivery trade.
 *
 * <p>Among the cards that do agree, the one that says the most wins: a negotiated plan outweighs
 * every published dimension combined, and an exchange outweighs segment and asset type together
 * because NSE and BSE levy different transaction charges.
 *
 * <p>Ties on specificity go to the later start date, which is the card published most recently for
 * that scope. A remaining tie is a data error and is refused by name — picking either would be
 * arbitrary, and whichever was picked would quietly price every trade in the window.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ChargeScheduleResolver {

    private static final int PLAN_CODE_WEIGHT = 8;
    private static final int EXCHANGE_WEIGHT = 4;
    private static final int SEGMENT_WEIGHT = 2;
    private static final int ASSET_TYPE_WEIGHT = 1;

    private final ChargeScheduleRepository chargeScheduleRepository;

    /**
     * Rate cards change monthly at most and this runs once per transaction, so the answer is held.
     * Misses are held too: backfilling a period with no card on file must not re-ask on every trade.
     * Evicted whenever a card is written.
     */
    private final Map<ScopeKey, Optional<ChargeScheduleEntity>> cache = new ConcurrentHashMap<>();

    public Optional<ChargeScheduleEntity> resolve(ChargeContext context) {
        return cache.computeIfAbsent(ScopeKey.of(context), key -> select(context));
    }

    /** Called when a schedule is published or superseded, so the next trade sees the new card. */
    public void evictAll() {
        cache.clear();
    }

    private Optional<ChargeScheduleEntity> select(ChargeContext context) {
        List<ChargeScheduleEntity> eligible = chargeScheduleRepository
                .findCandidates(context.brokerName(), context.transactionDate())
                .stream()
                .filter(candidate -> agrees(candidate, context))
                .toList();

        if (eligible.isEmpty()) {
            log.warn("No charge schedule for broker={} on {} matching assetType={}, segment={}, exchange={},"
                            + " planCode={}; transaction {} accrues no charges",
                    context.brokerName(), context.transactionDate(), context.assetType(), context.segment(),
                    context.exchange(), context.planCode(), context.transactionId());
            return Optional.empty();
        }

        int best = eligible.stream().mapToInt(ChargeScheduleResolver::specificity).max().orElseThrow();
        List<ChargeScheduleEntity> mostSpecific = eligible.stream()
                .filter(candidate -> specificity(candidate) == best)
                .toList();

        return Optional.of(mostRecent(mostSpecific, context));
    }

    private static ChargeScheduleEntity mostRecent(List<ChargeScheduleEntity> candidates, ChargeContext context) {
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }

        LocalDate latest = candidates.stream()
                .map(ChargeScheduleEntity::getStartDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<ChargeScheduleEntity> newest = candidates.stream()
                .filter(candidate -> Objects.equals(candidate.getStartDate(), latest))
                .toList();

        if (newest.size() > 1) {
            String codes = newest.stream()
                    .map(ChargeScheduleEntity::getScheduleCode)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("More than one charge schedule is equally specific for broker "
                    + context.brokerName() + " on " + context.transactionDate() + ": " + codes
                    + ". Close one of their validity windows.");
        }
        return newest.getFirst();
    }

    /** A declared dimension must agree; a null one matches anything. */
    private static boolean agrees(ChargeScheduleEntity schedule, ChargeContext context) {
        return matches(schedule.getAssetType(), context.assetType())
                && matches(schedule.getSegment(), context.segment())
                && matches(schedule.getExchange(), context.exchange())
                && matches(schedule.getPlanCode(), context.planCode());
    }

    private static boolean matches(Object declared, Object actual) {
        return declared == null || declared.equals(actual);
    }

    private static int specificity(ChargeScheduleEntity schedule) {
        int score = 0;
        if (schedule.getPlanCode() != null) {
            score += PLAN_CODE_WEIGHT;
        }
        if (schedule.getExchange() != null) {
            score += EXCHANGE_WEIGHT;
        }
        if (schedule.getSegment() != null) {
            score += SEGMENT_WEIGHT;
        }
        if (schedule.getAssetType() != null) {
            score += ASSET_TYPE_WEIGHT;
        }
        return score;
    }

    /** Everything the choice depends on, and nothing else — a looser key would answer wrongly. */
    private record ScopeKey(
            BrokerName brokerName,
            AssetType assetType,
            TradeSegment segment,
            String exchange,
            String planCode,
            LocalDate transactionDate) {

        static ScopeKey of(ChargeContext context) {
            return new ScopeKey(context.brokerName(), context.assetType(), context.segment(),
                    context.exchange(), context.planCode(), context.transactionDate());
        }
    }
}
