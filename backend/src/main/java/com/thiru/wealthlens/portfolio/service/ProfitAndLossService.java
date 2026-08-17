package com.thiru.wealthlens.portfolio.service;
import com.thiru.wealthlens.brokercharges.dto.context.BrokerChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerChargeTransactionType;
import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.brokercharges.service.UserBrokerChargeService;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossResponse;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitAndLossContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.model.BrokerChargesReport;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.entity.model.FortnightReport;
import com.thiru.wealthlens.portfolio.entity.model.MonthlyBrokerCharges;
import com.thiru.wealthlens.portfolio.entity.model.MonthlyReport;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.portfolio.entity.model.ReportModel;
import com.thiru.wealthlens.portfolio.entity.model.YearlyBrokerCharges;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import com.thiru.wealthlens.shared.util.collection.TOptional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
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

    private final ProfitAndLossRepository profitAndLossRepository;
    private final UserBrokerChargeService userBrokerChargeService;

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

		profitAndLossEntity.setLastUpdatedTime(LocalDateTime.now());
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

		int transactionYear = transactionDate.getYear();
		LocalDate financialYearEnd = financialYearEnd(transactionYear);

		if (transactionDate.isBefore(financialYearEnd)) {
			return (transactionYear - 1) + "-" + transactionYear;
		}

		return transactionYear + "-" + (transactionYear + 1);
	}

    private static LocalDate financialYearEnd(int year) {
        return LocalDate.of(year, MARCH, DAY_31);
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
        TransactionType transactionType = profitLossContext.transactionType();
        CorporateActionType actionType = profitLossContext.actionType();

        if (transactionType == TransactionType.SELL) {
            if (actionType == null) {
                handleNormalSellCase(userMail, profitLossContext);
            }
        } else if (transactionType == TransactionType.BUY) {
            handleNormalBuyCase(userMail, profitLossContext);
        } else {
            log.error("Invalid transaction type: {}", transactionType);
        }
    }

    private void handleNormalBuyCase(UserMail userMail, ProfitLossContext profitLossContext) {
        String email = userMail.getEmail();

        LocalDate transactionDate = profitLossContext.date();
        String financialYear = sanitizeFinancialYear(transactionDate);

        Optional<ProfitAndLossEntity> optionalProfitAndLoss = profitAndLossRepository.findByEmailAndFinancialYear(email, financialYear);
        ProfitAndLossEntity profitAndLossEntity = optionalProfitAndLoss.orElse(new ProfitAndLossEntity(email, financialYear));

        // calculate and update the broker charges
        if (profitLossContext.assetType() == AssetType.EQUITY) {
            BrokerChargeContext brokerChargeContext = brokerChargeContext(profitLossContext);
            UserBrokerCharges userBrokerCharges = userBrokerChargeService.addUserBrokerChargeEntry(userMail, brokerChargeContext);
            if (userBrokerCharges != null) {
                updateBrokerChargesReport(profitAndLossEntity, profitLossContext.accountType(), userBrokerCharges);
            }
        }
        profitAndLossRepository.save(profitAndLossEntity);
    }

    private void handleNormalSellCase(UserMail userMail, ProfitLossContext profitLossContext) {
        String email = userMail.getEmail();

        LocalDate transactionDate = profitLossContext.date();
        String financialYear = sanitizeFinancialYear(transactionDate);

        Optional<ProfitAndLossEntity> optionalProfitAndLoss = profitAndLossRepository.findByEmailAndFinancialYear(email, financialYear);
        ProfitAndLossEntity profitAndLossEntity = optionalProfitAndLoss.orElse(new ProfitAndLossEntity(email, financialYear));

        for (BuyContext buyContext : profitLossContext.buyContexts()) {
            boolean isShortTermHeld = isShortTermCapitalGain(buyContext.date(), transactionDate);
            double purchaseAmount = buyContext.price() * buyContext.quantity();
            double sellAmount = profitLossContext.price() * buyContext.quantity();
            InternalContext internalContext = new InternalContext(purchaseAmount, sellAmount, transactionDate, isShortTermHeld);
            updateProfitAndLossReport(profitAndLossEntity, profitLossContext, internalContext);
        }

        // calculate and update the broker charges
        if (profitLossContext.assetType() == AssetType.EQUITY) {
            BrokerChargeContext brokerChargeContext = brokerChargeContext(profitLossContext);
            UserBrokerCharges userBrokerCharges = userBrokerChargeService.addUserBrokerChargeEntry(userMail, brokerChargeContext);
            if (userBrokerCharges != null) {
                updateBrokerChargesReport(profitAndLossEntity, profitLossContext.accountType(), userBrokerCharges);
            }
        }
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

        profitAndLossEntity.setLastUpdatedTime(LocalDateTime.now());
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

        realisedProfits.setLastUpdatedTime(LocalDateTime.now());
        return realisedProfits;
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

    private static void updateBrokerChargesReport(ProfitAndLossEntity profitAndLossEntity, AccountType accountType, UserBrokerCharges userBrokerCharges) {
        if (accountType == AccountType.SELF) {
            RealisedProfits existingRealisedProfits = TOptional.mapO(profitAndLossEntity.getRealisedProfits(), RealisedProfits.empty());
            RealisedProfits calculatedProfitDetails = calculateBrokerChargesDetails(existingRealisedProfits, userBrokerCharges);
            profitAndLossEntity.setRealisedProfits(calculatedProfitDetails);
        } else {
            RealisedProfits outSourcedRealisedProfits = TOptional.mapO(profitAndLossEntity.getOutSourcedRealisedProfits(), RealisedProfits.empty());
            RealisedProfits calculatedProfitDetails = calculateBrokerChargesDetails(outSourcedRealisedProfits, userBrokerCharges);
            profitAndLossEntity.setOutSourcedRealisedProfits(calculatedProfitDetails);
        }

        profitAndLossEntity.setLastUpdatedTime(LocalDateTime.now());
    }

    private static RealisedProfits calculateBrokerChargesDetails(RealisedProfits realisedProfits, UserBrokerCharges userBrokerCharges) {
        YearlyBrokerCharges yearlyBrokerCharges = realisedProfits.getYearlyBrokerCharges();
        if (realisedProfits.getYearlyBrokerCharges() == null) {
            yearlyBrokerCharges = new YearlyBrokerCharges();
            realisedProfits.setYearlyBrokerCharges(yearlyBrokerCharges);
        }

        Map<Month, MonthlyBrokerCharges> monthlyBrokerCharges = updateMonthlyReport(yearlyBrokerCharges.getMonthlyReport(), userBrokerCharges);
        yearlyBrokerCharges.setMonthlyReport(monthlyBrokerCharges);

        updateYearlyBrokerCharges(yearlyBrokerCharges, userBrokerCharges);
        return realisedProfits;
    }

    private static Map<Month, MonthlyBrokerCharges> updateMonthlyReport(Map<Month, MonthlyBrokerCharges> monthlyBrokerCharges, UserBrokerCharges userBrokerCharges) {
        LocalDate transactionDate = userBrokerCharges.getTransactionDate();
        Month month = transactionDate.getMonth();
        MonthlyBrokerCharges monthlyBrokerCharge = monthlyBrokerCharges.getOrDefault(month, new MonthlyBrokerCharges(month));

        BrokerChargesReport fortnightReport;
        if (transactionDate.getDayOfMonth() <= 15) {
            fortnightReport = TOptional.mapO(monthlyBrokerCharge.getFirstHalfBrokerCharges(), new BrokerChargesReport());
            monthlyBrokerCharge.setFirstHalfBrokerCharges(fortnightReport);
        } else {
            fortnightReport = TOptional.mapO(monthlyBrokerCharge.getSecondHalfBrokerCharges(), new BrokerChargesReport());
            monthlyBrokerCharge.setSecondHalfBrokerCharges(fortnightReport);
        }

        updateFortnightBrokerCharges(fortnightReport, userBrokerCharges);
        updateMonthlyBrokerCharges(monthlyBrokerCharge, userBrokerCharges);
        monthlyBrokerCharges.put(month, monthlyBrokerCharge);

        return monthlyBrokerCharges;
    }

    private static void updateYearlyBrokerCharges(YearlyBrokerCharges yearlyBrokerCharges, UserBrokerCharges userBrokerCharges) {
        updateBrokerCharges(yearlyBrokerCharges, userBrokerCharges);
    }

    private static void updateMonthlyBrokerCharges(MonthlyBrokerCharges monthlyBrokerCharge, UserBrokerCharges userBrokerCharges) {
        updateBrokerCharges(monthlyBrokerCharge, userBrokerCharges);
    }

    private static void updateFortnightBrokerCharges(BrokerChargesReport fortnightBrokerChargesReport, UserBrokerCharges userBrokerCharges) {
        updateBrokerCharges(fortnightBrokerChargesReport, userBrokerCharges);
    }

    private static void updateBrokerCharges(BrokerChargesReport brokerChargesReport, UserBrokerCharges userBrokerCharges) {
        brokerChargesReport.setBrokerage(brokerChargesReport.getBrokerage() + userBrokerCharges.getBrokerage());
        brokerChargesReport.setAccountOpeningCharges(brokerChargesReport.getAccountOpeningCharges() + userBrokerCharges.getAccountOpeningCharges());
        brokerChargesReport.setAmcCharges(brokerChargesReport.getAmcCharges() + userBrokerCharges.getAmcCharges());
        brokerChargesReport.setGovtCharges(brokerChargesReport.getGovtCharges() + userBrokerCharges.getGovtCharges());
        brokerChargesReport.setTaxes(brokerChargesReport.getTaxes() + userBrokerCharges.getTaxes());
        brokerChargesReport.setDpCharges(brokerChargesReport.getDpCharges() + userBrokerCharges.getDpCharges());
    }

    private static boolean isShortTermCapitalGain(LocalDate buyDate, LocalDate sellDate) {
        LocalDate thresholdDate = buyDate.plusYears(1);
        return sellDate.isBefore(thresholdDate);
    }

    private static BrokerChargeContext brokerChargeContext(ProfitLossContext context) {
        BrokerChargeTransactionType transactionType = toBrokerChargeTransactionType(context.transactionType());
        double totalAmount = context.price() * context.quantity();
        return new BrokerChargeContext(context.transactionId(), context.stockCode(), context.brokerName(), transactionType,
                context.date(), context.exchangeName(), context.actionType(), totalAmount);
    }

    private static BrokerChargeTransactionType toBrokerChargeTransactionType(TransactionType type) {
        return switch (type) {
            case BUY -> BrokerChargeTransactionType.BUY;
            case SELL -> BrokerChargeTransactionType.SELL;
        };
    }

    public UserBrokerCharges updateProfitAndLossWithAmcCharges(UserMail userMail, BrokerChargeContext brokerChargeContext) {

        String email = userMail.getEmail();
        String financialYear = sanitizeFinancialYear(brokerChargeContext.transactionDate());

        Optional<ProfitAndLossEntity> optionalProfitAndLoss = profitAndLossRepository.findByEmailAndFinancialYear(email, financialYear);
        ProfitAndLossEntity profitAndLossEntity = optionalProfitAndLoss.orElse(new ProfitAndLossEntity(email, financialYear));

        UserBrokerCharges userBrokerCharges = userBrokerChargeService.addUserBrokerChargeEntry(userMail, brokerChargeContext);
        if (userBrokerCharges != null) {
            updateBrokerChargesReport(profitAndLossEntity, AccountType.SELF, userBrokerCharges);
        }
        profitAndLossRepository.save(profitAndLossEntity);
        return userBrokerCharges;
    }

    private record InternalContext(double purchaseAmount, double sellAmount,
                                   LocalDate sellDate, boolean isShortTermHeld) {
    }
}
