package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.context.BrokerChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerChargeTransactionType;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerageAggregatorType;
import com.thiru.wealthlens.brokercharges.entity.BrokerCharges;
import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.brokercharges.repository.UserBrokerChargesRepository;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.model.BrokerageCharges;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBrokerChargeService {

    private final UserBrokerChargesRepository userBrokerChargesRepository;
    private final BrokerChargeService brokerChargeService;

    public UserBrokerCharges addUserBrokerChargeEntry(UserMail userMail, BrokerChargeContext brokerChargeContext) {
        BrokerName brokerName = brokerChargeContext.brokerName();
        LocalDate transactionDate = brokerChargeContext.transactionDate();

        BrokerCharges brokerCharges = brokerChargeService.getBrokerCharge(brokerName, transactionDate);
        if (brokerCharges == null) {
            log.error("Broker charges not found for user: {}, brokerContext: {}", userMail, brokerChargeContext);
            return null;
        }

        UserBrokerCharges brokerCharge;
        if (brokerChargeContext.transactionType() == BrokerChargeTransactionType.AMC_CHARGES) {
            if (brokerCharges.getAmcChargeFrequency() == null) {
                log.error("AMC charge frequency not configured for broker charges id: {}", brokerCharges.getId());
                return null;
            }
            brokerCharge = getUserBrokerChargesForAmc(userMail, brokerChargeContext, brokerCharges);
        } else {
            brokerCharge = getUserBrokerCharges(userMail, brokerChargeContext, brokerCharges);
        }
        return userBrokerChargesRepository.save(brokerCharge);
    }

    public List<UserBrokerCharges> getUserBrokerCharges(UserMail userMail) {
        return userBrokerChargesRepository.findByEmail(userMail.getEmail());
    }

    private UserBrokerCharges getUserBrokerCharges(UserMail userMail, BrokerChargeContext brokerChargeContext, BrokerCharges brokerCharges) {
        UserBrokerCharges userBrokerCharges = new UserBrokerCharges();

        double dpCharges = 0;
        if (brokerChargeContext.transactionType() == BrokerChargeTransactionType.SELL) {
            dpCharges = getDpCharges(userMail, brokerChargeContext.brokerName(), brokerChargeContext.stockCode(), brokerChargeContext.transactionDate(), brokerCharges);
        }
        userBrokerCharges.setEmail(userMail.getEmail());
        userBrokerCharges.setBrokerName(brokerChargeContext.brokerName());
        userBrokerCharges.setStockCode(brokerChargeContext.stockCode());
        userBrokerCharges.setTransactionDate(brokerChargeContext.transactionDate());
        userBrokerCharges.setType(brokerChargeContext.transactionType());
        userBrokerCharges.setBrokerage(getBrokerage(brokerChargeContext, brokerCharges.getBrokerageCharges()));
        userBrokerCharges.setGovtCharges(getGovtCharges(brokerChargeContext, brokerCharges));
        userBrokerCharges.setDpCharges(dpCharges);
        userBrokerCharges.setTransactionId(brokerChargeContext.transactionId());

        // handle amc or account opening charges
//        setIfAmcOrAccountOpeningCharges(userBrokerCharges, brokerCharges, brokerChargeContext.transactionType());
        setTaxes(userBrokerCharges, brokerCharges);
        return userBrokerCharges;
    }

    public UserBrokerCharges getUserBrokerChargesForAmc(UserMail userMail, BrokerChargeContext brokerChargeContext, BrokerCharges brokerCharges) {
        UserBrokerCharges userBrokerCharges = new UserBrokerCharges();
        userBrokerCharges.setEmail(userMail.getEmail());
        userBrokerCharges.setBrokerName(brokerChargeContext.brokerName());
        userBrokerCharges.setTransactionDate(brokerChargeContext.transactionDate());
        userBrokerCharges.setType(BrokerChargeTransactionType.AMC_CHARGES);

        double annualAmcCharges = brokerCharges.getAmcChargesAnnually();
        AmcChargeFrequency frequency = brokerCharges.getAmcChargeFrequency();
        double amcCharges = switch (frequency) {
            case QUARTERLY -> annualAmcCharges / 4;
            case ANNUALLY -> annualAmcCharges;
        };
        userBrokerCharges.setAmcCharges(amcCharges);

        setTaxes(userBrokerCharges, brokerCharges);
        return userBrokerCharges;
    }

    private double getGovtCharges(BrokerChargeContext brokerChargeContext, BrokerCharges brokerCharges) {
        double sttCharge = brokerChargeContext.totalAmount() * brokerCharges.getStt() / 100;
        double sebiCharge = brokerChargeContext.totalAmount() * brokerCharges.getSebiCharges() / 100;
        double govtCharges = sttCharge + sebiCharge;

        var transactionType = brokerChargeContext.transactionType();
        if (transactionType == BrokerChargeTransactionType.BUY) {
            double stampDuty = brokerChargeContext.totalAmount() * brokerCharges.getStampDuty() / 100;
            govtCharges += stampDuty;
        }
        return govtCharges;
    }

    private static void setTaxes(UserBrokerCharges userBrokerCharges, BrokerCharges brokerCharges) {
        String gstDescription = brokerCharges.getGstApplicableDescription();
        if (gstDescription == null || gstDescription.isBlank()) {
            userBrokerCharges.setTaxes(0);
            return;
        }
        String[] taxComponents = gstDescription.split(",");
        double taxes = 0;
        for (String taxComponent : taxComponents) {
            taxes += getTaxComponentTax(userBrokerCharges, taxComponent);
        }
        userBrokerCharges.setTaxes(taxes);
    }

    private static double getTaxComponentTax(UserBrokerCharges userBrokerCharges, String taxComponent) {
        String[] taxComponents = taxComponent.split("-");
        double taxPercentage = Double.parseDouble(taxComponents[0].replace("%", ""));
        String taxComponentName = taxComponents[1];
        if ("brokerage".equalsIgnoreCase(taxComponentName)) {
            return userBrokerCharges.getBrokerage() * taxPercentage / 100;
        } else if ("dp_charges".equalsIgnoreCase(taxComponentName)) {
            return userBrokerCharges.getDpCharges() * taxPercentage / 100;
        } else if ("stt".equalsIgnoreCase(taxComponentName)) {
            return userBrokerCharges.getGovtCharges() * taxPercentage / 100;
        } else if ("amc_charges".equalsIgnoreCase(taxComponentName)) {
            return userBrokerCharges.getAmcCharges() * taxPercentage / 100;
        }

        log.error("Unknown tax component: {}", taxComponentName);
        return 0.0;
    }

    private double getDpCharges(UserMail userMail, BrokerName brokerName, String stockCode, LocalDate transactionDate, BrokerCharges brokerCharges) {
        var brokerCharge = userBrokerChargesRepository.findTopSellTxnByBrokerNameAndStockCodeAndTransactionDate(userMail.getEmail(), brokerName, stockCode, transactionDate);
        if (brokerCharge.isEmpty()) {
            return brokerCharges.getDpChargesPerScrip();
        }
        return 0.0;
    }

//    private static void setIfAmcOrAccountOpeningCharges(UserBrokerCharges userBrokerCharges, BrokerCharges brokerCharges, BrokerChargeTransactionType type) {
//        if (type == BrokerChargeTransactionType.AMC_CHARGES) {
//            AmcChargeFrequency amcChargeFrequency = brokerCharges.getAmcChargeFrequency();
//            if (amcChargeFrequency == AmcChargeFrequency.ANNUALLY) {
//                userBrokerCharges.setAmcCharges(brokerCharges.getAmcChargesAnnually());
//            } else if (amcChargeFrequency == AmcChargeFrequency.QUARTERLY) {
//                double amcCharges = brokerCharges.getAmcChargesAnnually() / 4;
//                userBrokerCharges.setAmcCharges(amcCharges);
//            } else {
//                log.error("Invalid amc charge frequency: {}", amcChargeFrequency);
//            }
//        } else if (type == BrokerChargeTransactionType.ACCOUNT_OPENING_CHARGES) {
//            userBrokerCharges.setAccountOpeningCharges(brokerCharges.getAccountOpeningCharges());
//        }
//    }

    private static double getBrokerage(BrokerChargeContext brokerChargeContext, BrokerageCharges brokerageCharges) {

        if (brokerageCharges == null) {
            return 0;
        }

        double brokeragePercentage = brokerageCharges.getBrokerage();
        double brokerageWithFromPercentage = brokerChargeContext.totalAmount() * brokeragePercentage / 100;
        double brokerageChargesAmount = brokerageCharges.getBrokerageCharges();

        if (brokerageCharges.getBrokerageAggregator() == BrokerageAggregatorType.MIN) {
            double tempBrokerage = Math.max(brokerageCharges.getMinimumBrokerage(), brokerageWithFromPercentage);
            return Math.min(tempBrokerage, brokerageChargesAmount);
        } else if (brokerageCharges.getBrokerageAggregator() == BrokerageAggregatorType.MAX) {
            double tempBrokerage = Math.min(brokerageCharges.getMaximumBrokerage(), brokerageWithFromPercentage);
            return Math.max(tempBrokerage, brokerageChargesAmount);
        }
        return 0;
    }

    public void deleteUserBrokerCharges(UserMail userMail) {
        userBrokerChargesRepository.deleteByEmail(userMail.getEmail());
    }
}
