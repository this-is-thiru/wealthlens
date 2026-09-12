package com.thiru.wealthlens.portfolio.service;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.entity.model.ChargeSummaryReport;
import com.thiru.wealthlens.brokercharges.entity.model.MonthlyChargeSummary;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossResponse;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitAndLossContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.entity.model.FortnightReport;
import com.thiru.wealthlens.portfolio.entity.model.MonthlyReport;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.portfolio.entity.model.ReportModel;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodService;
import com.thiru.wealthlens.portfolio.holding.TradeClassificationQuery;
import com.thiru.wealthlens.portfolio.holding.TradeClassifier;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import com.thiru.wealthlens.shared.util.collection.TOptional;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accumulates realised profit/loss figures per financial year. Class-level
 * @{@link Transactional} ensures save operations are atomic. Safe only when
 * MongoDB replica set + {@code app.mongodb.transactions-enabled=true} is set.
 */
@Log4j2
@AllArgsConstructor
@Service
@Transactional
public class ProfitAndLossService {

    private static final int MARCH = 3;
    private static final int DAY_31 = 31;

    /** The day a month's first fortnight ends, matching the report this sits beside. */
    private static final int FORTNIGHT_BOUNDARY = 15;

    private final ProfitAndLossRepository profitAndLossRepository;
    private final HoldingPeriodService holdingPeriodService;

    private final TradeClassifier tradeClassifier;
    private final ChargeRecordingGateway chargeRecordingGateway;

    /**
     * Updates the profit and loss aggregates for the given user and the financial year
     * inferred from the sell transaction date contained in the provided context.
     * <p>
     * Processing flow:
     * <ul>
     *   <li>Derives the financial year from the sell transaction date in {@link ProfitAndLossContext}.</li>
     *   <li>Loads (or creates) the {@link com.thiru.wealthlens.entity.ProfitAndLossEntity} for the user and year
     *       via {@link #getProfitAndLoss(UserMail, ProfitAndLossContext)}.</li>
     *   <li>Updates realized profit figures based on {@link com.thiru.wealthlens.dto.enums.AccountType}
     *       using {@link #updateProfitAndLossReport(com.thiru.wealthlens.entity.ProfitAndLossEntity, InternalTransactionContext)}.</li>
     *   <li>Persists the updated entity using {@link #profitAndLossRepository}.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Note: This overload is deprecated. Prefer the variant that accepts a sell transaction identifier
     * for better traceability and potential idempotency handling.
     * </p>
     *
     * @param userMail             the user identity containing the email used to partition P&amp;L documents; must not be null
     * @param profitAndLossContext the purchase/sell context used to compute realized profit; must not be null
     * @deprecated for removal; use {@link #updateProfitAndLoss(UserMail, ProfitLossContext)} instead
     */
    @Deprecated(forRemoval = true)
    public void updateProfitAndLoss(UserMail userMail, ProfitAndLossContext profitAndLossContext) {

        InternalTransactionContext internalContext = new InternalTransactionContext(profitAndLossContext);

        ProfitAndLossEntity profitAndLossEntity = getProfitAndLoss(userMail, profitAndLossContext);
        updateProfitAndLossReport(profitAndLossEntity, internalContext);
        profitAndLossRepository.save(profitAndLossEntity);
    }

	private ProfitAndLossEntity getProfitAndLoss(UserMail userMail, ProfitAndLossContext profitAndLossContext) {
		String email = userMail.getEmail();

		LocalDate transactionDate = profitAndLossContext.getSellContext().getTransactionDate();
		String financialYear = sanitizeFinancialYear(transactionDate);
		Optional<ProfitAndLossEntity> optionalProfitAndLoss = profitAndLossRepository.findByEmailAndFinancialYear(email,
				financialYear);

		ProfitAndLossEntity profitAndLossEntity = optionalProfitAndLoss.orElse(new ProfitAndLossEntity(email));
		if (profitAndLossEntity.getFinancialYear() == null) {
			profitAndLossEntity.setFinancialYear(financialYear);
		}
		return profitAndLossEntity;
	}

    private static void updateProfitAndLossReport(ProfitAndLossEntity profitAndLossEntity,
                                                  InternalTransactionContext internalContext) {

		AccountType accountType = internalContext.getProfitAndLossContext().getMetadata().getAccountType();
		if (accountType == AccountType.SELF) {
			RealisedProfits existingRealisedProfits = TOptional.mapO(profitAndLossEntity.getRealisedProfits(),
					RealisedProfits.empty());
			RealisedProfits calculatedProfitDetails = calculateProfitDetails(existingRealisedProfits, internalContext);
			profitAndLossEntity.setRealisedProfits(calculatedProfitDetails);
		} else {
			RealisedProfits outSourcedRealisedProfits = TOptional
					.mapO(profitAndLossEntity.getOutSourcedRealisedProfits(), RealisedProfits.empty());
			RealisedProfits calculatedProfitDetails = calculateProfitDetails(outSourcedRealisedProfits,
					internalContext);
			profitAndLossEntity.setOutSourcedRealisedProfits(calculatedProfitDetails);
		}
	}

    private static RealisedProfits calculateProfitDetails(RealisedProfits realisedProfits,
                                                          InternalTransactionContext internalContext) {

		if (internalContext.isShortTermGain()) {

			FinancialReport financialReport = TOptional.mapO(realisedProfits.getShortTermCapitalGains(),
					FinancialReport.empty());
			updateFinancialReport(financialReport, internalContext);
			realisedProfits.setShortTermCapitalGains(financialReport);
		} else {

			FinancialReport financialReport = TOptional.mapO(realisedProfits.getLongTermCapitalGains(),
					FinancialReport.empty());
			updateFinancialReport(financialReport, internalContext);
			realisedProfits.setLongTermCapitalGains(financialReport);
		}

		realisedProfits.setLastUpdatedTime(LocalDateTime.now());
		return realisedProfits;
	}

    private static void updateFinancialReport(FinancialReport financialReport, InternalTransactionContext internalContext) {
        Map<Month, MonthlyReport> monthlyReports = updateMonthlyReports(financialReport.getMonthlyReport(),
                internalContext);

		financialReport.setMonthlyReport(monthlyReports);
		updateReportMetadata(financialReport, internalContext);
	}

    private static void updateReportMetadata(ReportModel metadata, InternalTransactionContext internalContext) {
        metadata.setPurchaseAmount(metadata.getPurchaseAmount() + internalContext.getPurchasePrice());
        metadata.setSellAmount(metadata.getSellAmount() + internalContext.getSellPrice());
        metadata.setProfit(metadata.getProfit() + internalContext.getProfit());
        metadata.setBrokerage(metadata.getBrokerage() + internalContext.getBrokerCharges());
        metadata.setMiscCharges(metadata.getMiscCharges() + internalContext.getMiscCharges());
//        metadata.setLastUpdatedTime(LocalDateTime.now());
    }

    private static Map<Month, MonthlyReport> updateMonthlyReports(Map<Month, MonthlyReport> monthlyReports,
                                                                  InternalTransactionContext internalContext) {
        LocalDate transactionDate = internalContext.getProfitAndLossContext().getSellContext().getTransactionDate();
        Month month = transactionDate.getMonth();
        MonthlyReport monthlyReport = monthlyReports.getOrDefault(month, new MonthlyReport(month));

		FortnightReport fortnightReport;
		if (transactionDate.getDayOfMonth() <= 15) {
			fortnightReport = TOptional.mapO(monthlyReport.getFirstFortnightReport(), FortnightReport.from());
			monthlyReport.setFirstFortnightReport(fortnightReport);
		} else {
			fortnightReport = TOptional.mapO(monthlyReport.getSecondFortnightReport(), FortnightReport.from());
			monthlyReport.setSecondFortnightReport(fortnightReport);
		}

		updateFortnightReport(fortnightReport, internalContext);

		updateReportMetadata(monthlyReport, internalContext);
		monthlyReports.put(month, monthlyReport);

		return monthlyReports;
	}

    private static void updateFortnightReport(FortnightReport fortnightReport, InternalTransactionContext internalContext) {

		ProfitAndLossContext profitAndLossContext = internalContext.getProfitAndLossContext();

        double purchasePrice = profitAndLossContext.getPurchaseContext().getPrice()
                * profitAndLossContext.getSellContext().getQuantity();
        fortnightReport.setPurchaseAmount(fortnightReport.getPurchaseAmount() + purchasePrice);

        double sellPrice = profitAndLossContext.getSellContext().getPrice()
                * profitAndLossContext.getSellContext().getQuantity();
        fortnightReport.setSellAmount(fortnightReport.getSellAmount() + sellPrice);

        double purchaseBrokerCharges = profitAndLossContext.getPurchaseContext().getBrokerCharges();
        double sellBrokerCharges = profitAndLossContext.getSellContext().getBrokerCharges();
        double brokerCharges = purchaseBrokerCharges + sellBrokerCharges;
        fortnightReport.setBrokerage(fortnightReport.getBrokerage() + brokerCharges);

		double purchaseMiscCharges = profitAndLossContext.getPurchaseContext().getMiscCharges();
		double sellMiscCharges = profitAndLossContext.getSellContext().getMiscCharges();
		double miscCharges = purchaseMiscCharges + sellMiscCharges;
		fortnightReport.setMiscCharges(fortnightReport.getMiscCharges() + miscCharges);

		double netGainOrLoss = calculateGains(profitAndLossContext) - brokerCharges - miscCharges;
		fortnightReport.setProfit(fortnightReport.getProfit() + netGainOrLoss);

		// Update internal context
		internalContext.setPurchasePrice(purchasePrice);
		internalContext.setSellPrice(sellPrice);
		internalContext.setProfit(netGainOrLoss);
		internalContext.setBrokerCharges(brokerCharges);
		internalContext.setMiscCharges(miscCharges);
	}

	private static double calculateGains(ProfitAndLossContext profitAndLossContext) {

		double sellQuantity = profitAndLossContext.getSellContext().getQuantity();
		double initialPrice = profitAndLossContext.getPurchaseContext().getPrice();
		double currentPrice = profitAndLossContext.getSellContext().getPrice();

		return (currentPrice - initialPrice) * sellQuantity;
	}

	private static String sanitizeFinancialYear(LocalDate transactionDate) {
		return TLocalDate.financialYear(transactionDate);
	}


	public ProfitAndLossResponse getProfitAndLoss(UserMail userMail, String financialYear) {
		String email = userMail.getEmail();

		Optional<ProfitAndLossEntity> optionalProfitAndLoss = profitAndLossRepository.findByEmailAndFinancialYear(email,
				financialYear);
		ProfitAndLossEntity profitAndLossEntity = optionalProfitAndLoss.orElse(new ProfitAndLossEntity());

		return TJsonMapper.safeCopy(profitAndLossEntity, ProfitAndLossResponse.class);
	}

	public void deleteProfitAndLoss(UserMail userMail) {
		profitAndLossRepository.deleteByEmail(userMail.getEmail());
	}

    @Data
    @NoArgsConstructor
    private static class InternalTransactionContext {
        private boolean isShortTermGain;
        private double purchasePrice;
        private double sellPrice;
        private double profit;
        private double brokerCharges;
        private double miscCharges;
        private ProfitAndLossContext profitAndLossContext;

        InternalTransactionContext(ProfitAndLossContext profitAndLossContext) {
            this.profitAndLossContext = profitAndLossContext;
            isShortTermCapitalGain();
        }

		private void isShortTermCapitalGain() {

			LocalDate purchaseDate = profitAndLossContext.getPurchaseContext().getTransactionDate();
			LocalDate sellDate = profitAndLossContext.getSellContext().getTransactionDate();

            LocalDate thresholdDate = purchaseDate.plusYears(1);
            this.isShortTermGain = sellDate.isBefore(thresholdDate);
        }
    }

    /**
     * Updates the profit and loss aggregates for the given user and the financial year
     * inferred from the sell transaction date contained in the provided context.
     * <p>
     * Processing flow:
     * <ul>
     *   <li>Derives the financial year from the sell transaction date in {@link ProfitAndLossContext}.</li>
     *   <li>Loads (or creates) the {@link com.thiru.wealthlens.entity.ProfitAndLossEntity} for the user and year
     *       via {@link #getProfitAndLoss(UserMail, ProfitAndLossContext)}.</li>
     *   <li>Updates realized profit figures based on {@link com.thiru.wealthlens.dto.enums.AccountType}
     *       using {@link #updateProfitAndLossReport(com.thiru.wealthlens.entity.ProfitAndLossEntity, InternalTransactionContext)}.</li>
     *   <li>Persists the updated entity using {@link #profitAndLossRepository}.</li>
     * </ul>
     * </p>
     *
     * <p>
     * The {@code sellTransactionId} parameter allows callers to correlate this update with a specific
     * sell transaction for auditing, tracing, or idempotency at higher layers. This method does not
     * persist or otherwise use the identifier internally.
     * </p>
     *
     * @param userMail          the user identity containing the email used to partition P&amp;L documents; must not be null
     * @param profitLossContext the purchase/sell context used to compute realized profit; must not be null
     */
    public void updateProfitAndLoss(UserMail userMail, ProfitLossContext profitLossContext) {
        dispatch(userMail, profitLossContext, Optional.empty());
    }

    /**
     * The variant for a caller that has already priced the trade.
     *
     * <p>{@code PortfolioService.buyStockV2} computes the charge itself, because cost basis is set
     * from it and that has to happen before the lot is written (Chunk 10a). It then hands the result
     * here so the engine runs <b>once</b> per trade rather than once for cost basis and again for
     * the shadow record.
     *
     * @param precomputed the computation the caller already obtained, or {@code null} to price the
     *                    trade here as before. {@code Optional.empty()} is distinct from
     *                    {@code null}: it means the caller asked and the engine declined, so asking
     *                    again would only repeat the decline
     */
    public void updateProfitAndLoss(UserMail userMail, ProfitLossContext profitLossContext,
                                    Optional<ChargeComputation> precomputed) {
        dispatch(userMail, profitLossContext, precomputed);
    }

    private void dispatch(UserMail userMail, ProfitLossContext profitLossContext,
                          Optional<ChargeComputation> precomputed) {
        TransactionType transactionType = profitLossContext.transactionType();
        CorporateActionType actionType = profitLossContext.actionType();

        if (transactionType == TransactionType.SELL) {
            if (actionType == null) {
                handleNormalSellCase(userMail, profitLossContext, precomputed);
            }
        } else if (transactionType == TransactionType.BUY) {
            handleNormalBuyCase(userMail, profitLossContext, precomputed);
        } else {
            log.error("Invalid transaction type: {}", transactionType);
        }
    }

    private void handleNormalBuyCase(UserMail userMail, ProfitLossContext profitLossContext,
                                     Optional<ChargeComputation> precomputed) {
        String email = userMail.getEmail();

        LocalDate transactionDate = profitLossContext.date();
        String financialYear = sanitizeFinancialYear(transactionDate);

        Optional<ProfitAndLossEntity> optionalProfitAndLoss = profitAndLossRepository.findByEmailAndFinancialYear(email, financialYear);
        ProfitAndLossEntity profitAndLossEntity = optionalProfitAndLoss.orElse(new ProfitAndLossEntity(email, financialYear));

        // Phase B -- shadow recording. Deliberately outside the EQUITY gate below: that gate guards
        // the superseded implementation, which resolves a rate card by broker and date with no
        // asset-type dimension, so a mutual fund passed through it would be priced as equity. The
        // engine has that dimension, so every asset type reaches it (FR-8). The return value is
        // ignored -- nothing here may touch cost basis until Phase C.
        recordCharge(userMail, profitLossContext, precomputed);


        mergeChargeSummary(profitAndLossEntity, profitLossContext, precomputed);
        profitAndLossRepository.save(profitAndLossEntity);
    }

    private void handleNormalSellCase(UserMail userMail, ProfitLossContext profitLossContext,
                                      Optional<ChargeComputation> precomputed) {
        String email = userMail.getEmail();

        LocalDate transactionDate = profitLossContext.date();
        String financialYear = sanitizeFinancialYear(transactionDate);

        Optional<ProfitAndLossEntity> optionalProfitAndLoss = profitAndLossRepository.findByEmailAndFinancialYear(email, financialYear);
        ProfitAndLossEntity profitAndLossEntity = optionalProfitAndLoss.orElse(new ProfitAndLossEntity(email, financialYear));

        for (BuyContext buyContext : profitLossContext.buyContexts()) {
            // Two answers, deliberately. The classification is the real one and goes in the map;
            // the boolean is what the legacy pair has always been given and keeps it byte-identical
            // for existing readers until V1 goes and the pair goes with it (D8).
            CapitalGainsType classification = tradeClassifier.classify(new TradeClassificationQuery(
                    profitLossContext.stockCode(), profitLossContext.assetType(), profitLossContext.segment(),
                    buyContext.date(), transactionDate)).capitalGainsType();
            boolean isShortTermHeld = isShortTermCapitalGain(profitLossContext.assetType(), buyContext.date(), transactionDate);
            double purchaseAmount = buyContext.price() * buyContext.quantity();
            double sellAmount = profitLossContext.price() * buyContext.quantity();
            InternalContext internalContext = new InternalContext(purchaseAmount, sellAmount, transactionDate, isShortTermHeld, classification);
            updateProfitAndLossReport(profitAndLossEntity, profitLossContext, internalContext);
        }

        // Phase B -- shadow recording. Deliberately outside the EQUITY gate below: that gate guards
        // the superseded implementation, which resolves a rate card by broker and date with no
        // asset-type dimension, so a mutual fund passed through it would be priced as equity. The
        // engine has that dimension, so every asset type reaches it (FR-8). The return value is
        // ignored -- nothing here may touch cost basis until Phase C.
        recordCharge(userMail, profitLossContext, precomputed);


        mergeChargeSummary(profitAndLossEntity, profitLossContext, precomputed);
        profitAndLossRepository.save(profitAndLossEntity);
    }

    private static void updateProfitAndLossReport(ProfitAndLossEntity profitAndLossEntity, ProfitLossContext profitLossContext, InternalContext internalContext) {

        AccountType accountType = profitLossContext.accountType();
        if (accountType == AccountType.SELF) {
            RealisedProfits existingRealisedProfits = TOptional.mapO(profitAndLossEntity.getRealisedProfits(), RealisedProfits.empty());
            RealisedProfits calculatedProfitDetails = calculateProfitDetails(existingRealisedProfits, internalContext);
            profitAndLossEntity.setRealisedProfits(calculatedProfitDetails);
        } else {
            RealisedProfits outSourcedRealisedProfits = TOptional.mapO(profitAndLossEntity.getOutSourcedRealisedProfits(), RealisedProfits.empty());
            RealisedProfits calculatedProfitDetails = calculateProfitDetails(outSourcedRealisedProfits, internalContext);
            profitAndLossEntity.setOutSourcedRealisedProfits(calculatedProfitDetails);
        }
    }

    private static RealisedProfits calculateProfitDetails(RealisedProfits realisedProfits, InternalContext internalContext) {

        if (internalContext.isShortTermHeld()) {
            FinancialReport financialReport = TOptional.mapO(realisedProfits.getShortTermCapitalGains(), FinancialReport.empty());
            updateFinancialReport(financialReport, internalContext);
            realisedProfits.setShortTermCapitalGains(financialReport);
        } else {
            FinancialReport financialReport = TOptional.mapO(realisedProfits.getLongTermCapitalGains(), FinancialReport.empty());
            updateFinancialReport(financialReport, internalContext);
            realisedProfits.setLongTermCapitalGains(financialReport);
        }

        recordByClassification(realisedProfits, internalContext);

        realisedProfits.setLastUpdatedTime(LocalDateTime.now());
        return realisedProfits;
    }

    /**
     * The same amounts again, under the classification that is actually true (D8).
     *
     * <p>No branch: five classifications cannot be forced through an {@code if/else}, and that
     * forcing is precisely the defect this exists to end. A key appears only once a disposal has
     * been classified that way, so the map stays as small as the period's real variety.
     */
    private static void recordByClassification(RealisedProfits realisedProfits, InternalContext internalContext) {
        Map<CapitalGainsType, FinancialReport> byClassification =
                TOptional.mapO(realisedProfits.getGainsByClassification(), new EnumMap<>(CapitalGainsType.class));
        FinancialReport report = byClassification.computeIfAbsent(
                internalContext.classification(), _ -> FinancialReport.empty());
        updateFinancialReport(report, internalContext);
        realisedProfits.setGainsByClassification(byClassification);
    }

    private static void updateFinancialReport(FinancialReport financialReport, InternalContext internalContext) {
        Map<Month, MonthlyReport> monthlyReports = updateMonthlyReport(financialReport.getMonthlyReport(), internalContext);

        financialReport.setMonthlyReport(monthlyReports);
        updateYearlyTransactionReport(financialReport, internalContext);
    }

    private static Map<Month, MonthlyReport> updateMonthlyReport(Map<Month, MonthlyReport> monthlyReports, InternalContext internalContext) {
        LocalDate transactionDate = internalContext.sellDate();
        Month month = transactionDate.getMonth();
        MonthlyReport monthlyReport = monthlyReports.getOrDefault(month, new MonthlyReport(month));

        FortnightReport fortnightReport;
        if (transactionDate.getDayOfMonth() <= 15) {
            fortnightReport = TOptional.mapO(monthlyReport.getFirstFortnightReport(), FortnightReport.from());
            monthlyReport.setFirstFortnightReport(fortnightReport);
        } else {
            fortnightReport = TOptional.mapO(monthlyReport.getSecondFortnightReport(), FortnightReport.from());
            monthlyReport.setSecondFortnightReport(fortnightReport);
        }

        updateFortnightTransactionReport(fortnightReport, internalContext);
        updateMonthlyTransactionReport(monthlyReport, internalContext);
        monthlyReports.put(month, monthlyReport);

        return monthlyReports;
    }

    private static void updateYearlyTransactionReport(FinancialReport financialReport, InternalContext internalContext) {
        financialReport.setPurchaseAmount(financialReport.getPurchaseAmount() + internalContext.purchaseAmount());
        financialReport.setSellAmount(financialReport.getSellAmount() + internalContext.sellAmount());
    }

    private static void updateMonthlyTransactionReport(MonthlyReport monthlyReport, InternalContext internalContext) {
        monthlyReport.setPurchaseAmount(monthlyReport.getPurchaseAmount() + internalContext.purchaseAmount());
        monthlyReport.setSellAmount(monthlyReport.getSellAmount() + internalContext.sellAmount());
    }

    private static void updateFortnightTransactionReport(FortnightReport fortnightReport, InternalContext internalContext) {
        fortnightReport.setPurchaseAmount(fortnightReport.getPurchaseAmount() + internalContext.purchaseAmount());
        fortnightReport.setSellAmount(fortnightReport.getSellAmount() + internalContext.sellAmount());
    }

    /**
     * Resolved rather than assumed. This used to be {@code buyDate.plusYears(1)} applied to every
     * asset type, which is the listed-equity rule — mutual funds, bonds and gold bonds each follow a
     * different one, and some are short-term however long they are held.
     *
     * <p>The asset type comes from the trade's own context. The sub-class, which is what decides a
     * mutual fund's rule, is not available here and resolves as unclassified; the service logs that
     * and counts it short-term, which is the higher-taxed direction.
     */
    private boolean isShortTermCapitalGain(AssetType assetType, LocalDate buyDate, LocalDate sellDate) {
        return holdingPeriodService.isShortTerm(assetType, null, buyDate, sellDate);
    }

    private record InternalContext(double purchaseAmount, double sellAmount, LocalDate sellDate,
                                   boolean isShortTermHeld, CapitalGainsType classification) {
    }

    /** Prices the trade, unless the caller already did. */
    private void recordCharge(UserMail userMail, ProfitLossContext profitLossContext,
                              Optional<ChargeComputation> precomputed) {
        if (precomputed.isEmpty()) {
            chargeRecordingGateway.record(userMail, profitLossContext);
        }
    }

    /**
     * Folds one trade's computed charges into the period's summary.
     *
     * <p>Only when a computation was <b>passed in</b>, which is the V2 flow. V1 reaches the two-arg
     * overload, prices its trade through the gateway exactly as before and writes no summary: it is
     * a live path and this chunk does not change what it stores.
     *
     * <p>The account split mirrors the report this sits beside — {@code SELF} into
     * {@code realisedProfits}, anything else into {@code outSourcedRealisedProfits} — so the two can
     * be compared bucket for bucket while both are being written.
     *
     * <p>Accumulates rather than recalculates, which is the model {@code ChargeSummaryReport} was
     * built for. The consequence is that reprocessing one trade twice counts it twice; the hierarchy
     * beside it has always had that property. Deriving the summary from {@code user_charges}
     * instead would be idempotent by construction, and is the alternative to revisit when the old
     * hierarchy is deleted.
     */
    private static void mergeChargeSummary(ProfitAndLossEntity profitAndLossEntity,
                                           ProfitLossContext profitLossContext,
                                           Optional<ChargeComputation> precomputed) {
        if (precomputed.isEmpty()) {
            return;
        }
        Map<String, Double> amountByCode = precomputed.get().amountByCode();
        if (amountByCode.isEmpty()) {
            return;
        }

        boolean self = profitLossContext.accountType() == AccountType.SELF;
        RealisedProfits realisedProfits = self
                ? TOptional.mapO(profitAndLossEntity.getRealisedProfits(), RealisedProfits.empty())
                : TOptional.mapO(profitAndLossEntity.getOutSourcedRealisedProfits(), RealisedProfits.empty());

        YearlyChargeSummary yearly = realisedProfits.getYearlyChargeSummary();
        if (yearly == null) {
            yearly = new YearlyChargeSummary();
            realisedProfits.setYearlyChargeSummary(yearly);
        }
        yearly.merge(amountByCode);
        mergeIntoMonth(yearly, profitLossContext.date(), amountByCode);

        if (self) {
            profitAndLossEntity.setRealisedProfits(realisedProfits);
        } else {
            profitAndLossEntity.setOutSourcedRealisedProfits(realisedProfits);
        }
    }

    /** A fortnight stays null until something is charged in it, so an empty half reads as empty. */
    private static void mergeIntoMonth(YearlyChargeSummary yearly, LocalDate transactionDate,
                                       Map<String, Double> amountByCode) {
        Month month = transactionDate.getMonth();
        MonthlyChargeSummary monthly = yearly.getMonthlyReport()
                .computeIfAbsent(month, MonthlyChargeSummary::new);

        ChargeSummaryReport half;
        if (transactionDate.getDayOfMonth() <= FORTNIGHT_BOUNDARY) {
            half = TOptional.mapO(monthly.getFirstHalfCharges(), new ChargeSummaryReport());
            monthly.setFirstHalfCharges(half);
        } else {
            half = TOptional.mapO(monthly.getSecondHalfCharges(), new ChargeSummaryReport());
            monthly.setSecondHalfCharges(half);
        }

        half.merge(amountByCode);
        monthly.merge(amountByCode);
    }
}
