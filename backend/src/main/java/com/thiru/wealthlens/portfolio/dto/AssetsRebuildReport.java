package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One user's lots as stored and as a replay of their transactions rebuilds them, lot against lot, and what a real run
 * wrote (W-283a2, D-582; W-283a3, D-586; {@code docs/trade-ledger/assets-rebuild.md}). Each list names what the replay
 * had to read from somewhere other than the trade itself, so a difference can be traced.
 *
 * @param dryRun whether nothing was written
 * @param lotsWritten lots saved by a real run; 0 in a dry run
 * @param lotsKeptById rebuilt lots paired with a stored one, which keep its id
 * @param newLots rebuilt lots no stored lot pairs with, which get a new id, as "stock date buys"
 * @param lots every lot stored or rebuilt, matched by stock, broker, holder, date and buy transactions
 * @param lotsDiffering lots whose figures differ, or that exist on one side only
 * @param shapeFromStoredLots buys with no recorded flow whose lot shape was read from the stored lot listing them
 * @param shapeNotFound buys with no recorded flow that no stored lot lists, given their own lot
 * @param saleFlowFromOutcomes sales with no recorded flow whose flow was read from their {@code trade_outcomes} rows
 * @param saleFlowUnknown sales with no recorded flow and no rows, replayed as V2 (charges left whole)
 * @param actionsApplied splits and demergers replayed, as "type stock record-date broker"
 * @param actionsPending splits and demergers not yet performed for the user, left out
 * @param temporariesSkipped trades still waiting on a corporate action
 */
public record AssetsRebuildReport(String email, boolean dryRun, int lotsDiffering, int lotsWritten, int lotsKeptById,
        List<String> newLots, List<LotComparison> lots,
        List<String> shapeFromStoredLots, List<String> shapeNotFound, List<String> saleFlowFromOutcomes,
        List<String> saleFlowUnknown, List<String> actionsApplied, List<String> actionsPending, int temporariesSkipped) {

    public AssetsRebuildReport {
        newLots = List.copyOf(newLots);
        lots = List.copyOf(lots);
        shapeFromStoredLots = List.copyOf(shapeFromStoredLots);
        shapeNotFound = List.copyOf(shapeNotFound);
        saleFlowFromOutcomes = List.copyOf(saleFlowFromOutcomes);
        saleFlowUnknown = List.copyOf(saleFlowUnknown);
        actionsApplied = List.copyOf(actionsApplied);
        actionsPending = List.copyOf(actionsPending);
    }

    /**
     * One lot on each side; a side is empty when the lot exists only on the other.
     *
     * @param differingFields the figures that differ, by name; empty when the lot is the same on both sides
     */
    public record LotComparison(String stockCode, BrokerName brokerName, String accountHolder, LocalDate transactionDate,
            List<String> buyTransactionIds, Optional<LotFigures> stored, Optional<LotFigures> rebuilt,
            List<String> differingFields) {

        public LotComparison {
            buyTransactionIds = List.copyOf(buyTransactionIds);
            differingFields = List.copyOf(differingFields);
        }
    }

    /** The figures compared; order times and the list of actions on a lot are not (the design says why). */
    public record LotFigures(double quantity, double priceRupees, double brokerChargesRupees, double miscChargesRupees,
            double allocatedBuyChargesRupees, double allocatedBuyMiscChargesRupees,
            Optional<CorporateActionType> corporateActionType) {
    }
}
