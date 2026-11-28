package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossRebuildReport;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossRebuildReport.YearComparison;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossRebuildReport.YearTotals;
import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import com.thiru.wealthlens.shared.util.money.TMoney;
import com.thiru.wealthlens.shared.util.time.FinancialYear;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomputes one user's {@code profit_and_loss} from the rows a trade stores, so a correction to {@code transactions}
 * is followed by a rebuild rather than a hand edit of nested documents (B-4; W-283, D-575).
 *
 * <p>Gains come from {@code trade_outcomes}, charges from {@code user_charges} as they were recorded. Nothing is
 * priced again (ADR-32), and both are folded through {@link ProfitAndLossService}'s own steps, so a rebuild and a
 * live V2 trade cannot compute differently. Only a buy's or a sale's charges are folded, as on the live path; an AMC
 * cycle and the other account events never reach the P&amp;L.
 *
 * <p>A real run leaves every year's document holding exactly what the replay computed for it, so it is idempotent
 * by construction and also folds duplicates of one year into one document (W-337e: in place, under the stored
 * document's own id, not by deleting and reinserting — see {@link #write}).
 */
@Service
@Log4j2
@Transactional
@RequiredArgsConstructor
public class ProfitAndLossRebuild {

    private static final Set<ChargeEvent> TRADE_EVENTS = EnumSet.of(ChargeEvent.BUY, ChargeEvent.SELL);

    private final ProfitAndLossRepository profitAndLossRepository;
    private final TradeOutcomeRepository tradeOutcomeRepository;
    private final TransactionRepository transactionRepository;
    private final UserChargeService userChargeService;
    private final Clock clock;

    /**
     * Folds the user's stored rows into fresh documents and compares them with what is stored; unless a dry run,
     * replaces the stored documents with them.
     *
     * @throws IllegalArgumentException if a charge row names a transaction that is not stored, or a sale row records
     *     no asset type
     */
    public ProfitAndLossRebuildReport rebuild(UserMail userMail, boolean dryRun) {
        String email = userMail.getEmail();
        Map<String, ProfitAndLossEntity> rebuilt = new TreeMap<>();
        LocalDateTime rebuiltAt = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));

        List<TradeOutcomeEntity> sales = tradeOutcomeRepository.findByEmail(email).stream()
                .sorted(Comparator.comparing(TradeOutcomeEntity::getSellDate).thenComparing(TradeOutcomeEntity::getId))
                .toList();
        for (TradeOutcomeEntity sale : sales) {
            TradeOutcomeContext row = TJsonMapper.copy(sale, TradeOutcomeContext.class);
            ProfitAndLossEntity document = documentFor(rebuilt, email, row.getSellDate());
            ProfitAndLossService.foldSale(document, row, rebuiltAt);
        }

        List<UserChargeEntity> charges = userChargeService.findHistory(email).stream()
                .filter(charge -> TRADE_EVENTS.contains(charge.getEvent()))
                .sorted(Comparator.comparing(UserChargeEntity::getTransactionDate)
                        .thenComparing(UserChargeEntity::getTransactionId))
                .toList();
        Map<String, AccountType> accountTypes = accountTypesOf(charges);
        for (UserChargeEntity charge : charges) {
            ProfitAndLossService.foldCharges(documentFor(rebuilt, email, charge.getTransactionDate()),
                    accountTypes.get(charge.getTransactionId()), charge.getTransactionDate(), charge.getAmountByCode());
        }

        List<ProfitAndLossEntity> stored = profitAndLossRepository.findAllByEmail(email);
        ProfitAndLossRebuildReport report = new ProfitAndLossRebuildReport(dryRun, email, sales.size(), charges.size(),
                compare(stored, rebuilt));
        if (!dryRun) {
            write(email, stored, rebuilt);
        }
        return report;
    }

    /**
     * A year with a stored document is updated in place, under that document's id, rather than deleted and
     * reinserted: both land in the same flush, and Hibernate runs inserts before deletes, so a blind
     * delete-then-insert on {@code pnl_user_year_idx}'s {email, financial_year} would collide with the row it is
     * about to delete (W-337e). A year with no rebuilt document no longer has any sale or charge and is deleted; a
     * year stored more than once (a race before the version field) keeps one of its documents and deletes the rest,
     * so duplicates still fold into one.
     */
    private void write(String email, List<ProfitAndLossEntity> stored, Map<String, ProfitAndLossEntity> rebuilt) {
        Map<String, List<ProfitAndLossEntity>> storedByYear = new HashMap<>();
        stored.forEach(document -> storedByYear
                .computeIfAbsent(document.getFinancialYear(), year -> new ArrayList<>()).add(document));
        List<String> idsToDelete = new ArrayList<>();
        List<ProfitAndLossEntity> toSave = new ArrayList<>();
        rebuilt.forEach((year, built) -> {
            List<ProfitAndLossEntity> existingForYear = storedByYear.remove(year);
            if (existingForYear == null) {
                toSave.add(built);
                return;
            }
            ProfitAndLossEntity existing = existingForYear.getFirst();
            existing.setRealisedProfits(built.getRealisedProfits());
            existing.setOutSourcedRealisedProfits(built.getOutSourcedRealisedProfits());
            toSave.add(existing);
            existingForYear.stream().skip(1).map(ProfitAndLossEntity::getId).forEach(idsToDelete::add);
        });
        storedByYear.values().stream().flatMap(List::stream).map(ProfitAndLossEntity::getId).forEach(idsToDelete::add);
        if (!idsToDelete.isEmpty()) {
            profitAndLossRepository.deleteAllById(idsToDelete);
        }
        profitAndLossRepository.saveAll(toSave);
        log.info("Rebuilt profit_and_loss for {}: {} stored documents replaced by {}", email, stored.size(),
                rebuilt.size());
    }

    private static ProfitAndLossEntity documentFor(Map<String, ProfitAndLossEntity> rebuilt, String email,
                                                   LocalDate date) {
        String financialYear = FinancialYear.containing(date).label();
        return rebuilt.computeIfAbsent(financialYear, year -> new ProfitAndLossEntity(email, year));
    }

    /** A trade's account type is on its transaction, not its charge row; a live trade read it from the same place. */
    private Map<String, AccountType> accountTypesOf(List<UserChargeEntity> charges) {
        List<String> transactionIds = charges.stream().map(UserChargeEntity::getTransactionId).distinct().toList();
        Map<String, AccountType> accountTypes = new HashMap<>();
        transactionRepository.findAllById(transactionIds)
                .forEach(transaction -> accountTypes.put(transaction.getId(), transaction.getAccountType()));
        List<String> missing = transactionIds.stream().filter(id -> !accountTypes.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Charge rows name transactions that are not stored: " + missing
                    + "; delete those user_charges rows, or restore the transactions, before rebuilding");
        }
        return accountTypes;
    }

    private static List<YearComparison> compare(List<ProfitAndLossEntity> stored,
                                                Map<String, ProfitAndLossEntity> rebuilt) {
        Map<String, List<ProfitAndLossEntity>> storedByYear = new HashMap<>();
        stored.forEach(document -> storedByYear
                .computeIfAbsent(document.getFinancialYear(), year -> new ArrayList<>()).add(document));
        SortedSet<String> years = new TreeSet<>(storedByYear.keySet());
        years.addAll(rebuilt.keySet());

        List<YearComparison> comparisons = new ArrayList<>();
        for (String year : years) {
            List<ProfitAndLossEntity> storedDocuments = storedByYear.getOrDefault(year, List.of());
            List<ProfitAndLossEntity> rebuiltDocuments = Stream.ofNullable(rebuilt.get(year)).toList();
            comparisons.add(new YearComparison(year, !storedDocuments.isEmpty(), totalsOf(storedDocuments),
                    !rebuiltDocuments.isEmpty(), totalsOf(rebuiltDocuments)));
        }
        return comparisons;
    }

    /** Sums every split of the documents; two stored for one year (a race before the version field) add up. */
    private static YearTotals totalsOf(List<ProfitAndLossEntity> documents) {
        List<RealisedProfits> splits = documents.stream()
                .flatMap(document -> Stream.of(document.getRealisedProfits(), document.getOutSourcedRealisedProfits()))
                .filter(Objects::nonNull)
                .toList();
        YearTotals totals = YearTotals.NONE;
        for (RealisedProfits split : splits) {
            List<FinancialReport> reports = Objects.requireNonNullElse(split.getGainsByAssetAndSegment(),
                            Map.<AssetType, Map<TradeSegment, FinancialReport>>of()).values().stream()
                    .flatMap(bySegment -> bySegment.values().stream())
                    .toList();
            for (FinancialReport report : reports) {
                totals = plusGains(totals, report);
            }
            YearlyChargeSummary charges = split.getYearlyChargeSummary();
            if (charges != null) {
                totals = new YearTotals(totals.purchaseAmountRupees(), totals.sellAmountRupees(),
                        totals.profitRupees(), totals.brokerageRupees(), totals.miscChargesRupees(),
                        TMoney.add(totals.chargesRupees(), charges.getTotalCharges()));
            }
        }
        return totals;
    }

    private static YearTotals plusGains(YearTotals totals, FinancialReport report) {
        return new YearTotals(TMoney.add(totals.purchaseAmountRupees(), report.getPurchaseAmount()),
                TMoney.add(totals.sellAmountRupees(), report.getSellAmount()),
                TMoney.add(totals.profitRupees(), report.getProfit()),
                TMoney.add(totals.brokerageRupees(), report.getBrokerage()),
                TMoney.add(totals.miscChargesRupees(), report.getMiscCharges()),
                totals.chargesRupees());
    }
}
