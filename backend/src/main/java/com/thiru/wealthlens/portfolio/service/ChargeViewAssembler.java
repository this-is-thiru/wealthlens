package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.charges.AssetCharges;
import com.thiru.wealthlens.portfolio.dto.charges.ChargeNote;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.util.money.TMoney;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Attaches recorded charges to the transaction and holding responses.
 *
 * <p><b>It reads; it never allocates.</b> The hard part — splitting a sell's charges across the
 * FIFO lots it consumed so the parts sum to the whole — was solved when the sell was recorded, by
 * {@code TradeOutcomeRecorder.allocate}, and stored per lot in {@code trade_outcomes}. Deriving a
 * second split here would produce a figure that disagrees with the ledger the tax statement is
 * filed from. So the sell side is summed from what is stored, and nothing is re-derived.
 *
 * <p><b>Load once, assemble many.</b> A portfolio response carries hundreds of rows. Every lookup
 * goes through a {@link ChargeLookup} built by one query per side, because the obvious shape — ask
 * per row — is hundreds of round trips for one screen.
 */
@Service
@RequiredArgsConstructor
public class ChargeViewAssembler {

    private final UserChargeService userChargeService;
    private final TradeOutcomeRepository tradeOutcomeRepository;

    /**
     * Every charge row a response needs, fetched up front.
     *
     * @param byTransactionId  charge rows keyed by the trade they priced
     * @param bySourceBuyLotId realised rows keyed by the buy lot they consumed
     */
    public record ChargeLookup(Map<String, UserChargeEntity> byTransactionId,
                               Map<String, List<TradeOutcomeEntity>> bySourceBuyLotId) {

        public static ChargeLookup empty() {
            return new ChargeLookup(Map.of(), Map.of());
        }
    }

    /** The charge rows for a set of trades. One query. */
    public ChargeLookup forTransactions(String email, Collection<String> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return ChargeLookup.empty();
        }
        return new ChargeLookup(userChargeService.findForTransactions(email, transactionIds), Map.of());
    }

    /**
     * The buy-side charge rows and the realised rows for a set of lots. Two queries, whatever the
     * number of lots.
     */
    public ChargeLookup forLots(String email, Collection<AssetEntity> lots) {
        if (lots == null || lots.isEmpty()) {
            return ChargeLookup.empty();
        }
        Set<String> lotIds = lots.stream()
                .map(AssetEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> buyTransactionIds = lots.stream()
                .map(AssetEntity::getBuyTransactionIds)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        Map<String, List<TradeOutcomeEntity>> outcomes = lotIds.isEmpty() ? Map.of()
                : tradeOutcomeRepository.findByEmailAndSourceBuyLotIdIn(email, lotIds).stream()
                        .collect(Collectors.groupingBy(TradeOutcomeEntity::getSourceBuyLotId));

        return new ChargeLookup(userChargeService.findForTransactions(email, buyTransactionIds), outcomes);
    }

    /**
     * One trade's contract note, read back unchanged.
     *
     * <p>Null when no row exists. A trade made on the V1 path, or before the engine was switched
     * on, has none and — ADR-32 — never will; a zero there would be indistinguishable from a trade
     * correctly charged nothing, such as a bonus allotment.
     */
    public ChargeNote transactionNote(ChargeLookup lookup, String transactionId) {
        UserChargeEntity charge = lookup.byTransactionId().get(transactionId);
        return charge == null ? null : verbatimNote(charge);
    }

    /**
     * What a holding has cost in charges.
     *
     * <p>{@code lots} is a group as the response presents it: one entry for a per-lot endpoint,
     * several where the portfolio view has grouped a scrip by broker. A group of one keeps its
     * verbatim buy note; a group of several cannot, and says so.
     */
    public AssetCharges assetCharges(ChargeLookup lookup, List<AssetEntity> lots) {
        if (lots == null || lots.isEmpty()) {
            return null;
        }
        ChargeNote buy = buyNote(lookup, lots);
        List<TradeOutcomeEntity> outcomes = lots.stream()
                .map(AssetEntity::getId)
                .map(id -> lookup.bySourceBuyLotId().getOrDefault(id, List.of()))
                .flatMap(List::stream)
                .toList();
        ChargeNote sell = sellNote(outcomes);

        if (buy == null && sell == null) {
            return null;
        }

        AssetCharges charges = new AssetCharges();
        charges.setBuy(buy);
        charges.setSell(sell);
        charges.setDeductibleSellCharges(TMoney.scale(
                outcomes.stream().mapToDouble(TradeOutcomeEntity::getDeductibleSellCharges).sum()));
        charges.setAllocatedOutBuyCharges(TMoney.scale(
                lots.stream().mapToDouble(AssetEntity::getAllocatedBuyCharges).sum()));
        charges.setTotal(TMoney.add(buy == null ? 0.0 : buy.getTotal(), sell == null ? 0.0 : sell.getTotal()));
        return charges;
    }

    /** The buy side: verbatim for a single lot, merged by code for a grouped row. */
    private static ChargeNote buyNote(ChargeLookup lookup, List<AssetEntity> lots) {
        List<UserChargeEntity> charges = lots.stream()
                .map(AssetEntity::getBuyTransactionIds)
                .filter(ids -> ids != null && !ids.isEmpty())
                .map(List::getFirst)
                .map(id -> lookup.byTransactionId().get(id))
                .filter(Objects::nonNull)
                .toList();

        if (charges.isEmpty()) {
            return null;
        }
        if (charges.size() == 1) {
            return verbatimNote(charges.getFirst());
        }
        return mergedNote(charges.stream().map(UserChargeEntity::getAmountByCode).toList(), charges.size());
    }

    /**
     * The sell side, summed from the allocations already stored against each lot.
     *
     * <p>Never verbatim, however few rows there are: even a single realised row holds a share of a
     * disposal rather than the disposal itself.
     */
    private static ChargeNote sellNote(List<TradeOutcomeEntity> outcomes) {
        if (outcomes.isEmpty()) {
            return null;
        }
        return mergedNote(outcomes.stream().map(TradeOutcomeEntity::getSellChargeBreakup).toList(), outcomes.size());
    }

    private static ChargeNote verbatimNote(UserChargeEntity charge) {
        ChargeNote note = new ChargeNote();
        note.setVerbatim(true);
        note.setResolution(charge.getResolution());
        note.setLines(charge.getLines());
        note.setByCode(new LinkedHashMap<>(
                charge.getAmountByCode() == null ? Map.of() : charge.getAmountByCode()));
        note.setTotal(charge.getTotalCharges());
        note.setSourceCount(1);
        return note;
    }

    /**
     * Several breakdowns added together, code by code.
     *
     * <p>The total is summed from the merged codes rather than from the source rows' own totals, so
     * the figure and the breakdown beside it cannot disagree.
     */
    private static ChargeNote mergedNote(List<Map<String, Double>> breakups, int sourceCount) {
        Map<String, Double> byCode = new LinkedHashMap<>();
        for (Map<String, Double> breakup : breakups) {
            if (breakup == null) {
                continue;
            }
            breakup.forEach((code, amount) ->
                    byCode.merge(code, amount == null ? 0.0 : amount, TMoney::add));
        }
        ChargeNote note = new ChargeNote();
        note.setVerbatim(false);
        note.setByCode(byCode);
        note.setTotal(TMoney.sum(new ArrayList<>(byCode.values())));
        note.setSourceCount(sourceCount);
        return note;
    }
}
