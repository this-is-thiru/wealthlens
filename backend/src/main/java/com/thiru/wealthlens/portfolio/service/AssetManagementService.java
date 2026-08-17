package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.brokercharges.dto.context.BrokerChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.enums.BrokerChargeTransactionType;
import com.thiru.wealthlens.brokercharges.dto.request.AssetManagementDetailsRequest;
import com.thiru.wealthlens.portfolio.entity.AssetManagementDetails;
import com.thiru.wealthlens.portfolio.repository.AssetManagementRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
@Transactional
public class AssetManagementService {

    private static final int DAYS_CONSIDER_FOR_QUARTERLY_AMC_CHARGES = 91;

    private final AssetManagementRepository assetManagementRepository;
    private final ProfitAndLossService profitAndLossService;

    public void imposeAmcCharges() {
        LocalDate today = LocalDate.now();
        processEntriesWithQuarterlyFrequency(today);
        processEntriesWithAnnualFrequency(today);
    }

    public void processEntriesWithQuarterlyFrequency(LocalDate today) {
        LocalDate lastDayToConsiderForUpdate = today.minusDays(DAYS_CONSIDER_FOR_QUARTERLY_AMC_CHARGES);
        var assetManagementDetails = assetManagementRepository.findEntriesToUpdateAmcCharges(AmcChargeFrequency.QUARTERLY, lastDayToConsiderForUpdate);

        for (AssetManagementDetails assetManagementDetail : assetManagementDetails) {
            LocalDate fromDate = assetManagementDetail.getLastAmcChargesDeductedOn();
            LocalDate toDate = fromDate.plusDays(DAYS_CONSIDER_FOR_QUARTERLY_AMC_CHARGES);
            assetManagementDetail.setLastAmcChargesDeductedOn(toDate);
            var amcChargesEvent = imposeAmcCharges(assetManagementDetail, fromDate);
            assetManagementDetail.getAmcChargesEvents().add(amcChargesEvent);
            assetManagementRepository.save(assetManagementDetail);
        }
    }

    public void processEntriesWithAnnualFrequency(LocalDate today) {
        LocalDate lastDayToConsiderForUpdate = today.minusYears(1);
        var assetManagementDetails = assetManagementRepository.findEntriesToUpdateAmcCharges(AmcChargeFrequency.ANNUALLY, lastDayToConsiderForUpdate);

        for (AssetManagementDetails assetManagementDetail : assetManagementDetails) {
            LocalDate fromDate = assetManagementDetail.getLastAmcChargesDeductedOn();
            LocalDate toDate = fromDate.plusYears(1);
            assetManagementDetail.setLastAmcChargesDeductedOn(toDate);
            var amcChargesEvent = imposeAmcCharges(assetManagementDetail, fromDate);
            assetManagementDetail.getAmcChargesEvents().add(amcChargesEvent);
            assetManagementRepository.save(assetManagementDetail);
        }
    }

    private AssetManagementDetails.AmcChargesEvent imposeAmcCharges(AssetManagementDetails assetManagementDetail, LocalDate fromDate) {

        LocalDate transactionDate = assetManagementDetail.getLastAmcChargesDeductedOn();

        BrokerChargeContext brokerChargeContext = new BrokerChargeContext(null, null, assetManagementDetail.getBrokerName(),
                BrokerChargeTransactionType.AMC_CHARGES, transactionDate, null, null, 0);
        var userBrokerCharges = profitAndLossService.updateProfitAndLossWithAmcCharges(UserMail.from(assetManagementDetail.getEmail()), brokerChargeContext);
        double totalAmount = userBrokerCharges.getAmcCharges() + userBrokerCharges.getTaxes();
        return new AssetManagementDetails.AmcChargesEvent(userBrokerCharges.getId(), transactionDate.plusDays(1), totalAmount, List.of(fromDate, transactionDate));
    }

    public void addAssetManagementEntry(UserMail userMail, AssetManagementDetailsRequest request) {
        if (request.getLastAmcChargesDeductedOn() == null) {
            throw new IllegalArgumentException("Last Amc Charges Deducted On cannot be null");
        }
        Optional<AssetManagementDetails> assetManagementDetailsOpt = assetManagementRepository.findByEmailAndBrokerName(userMail.getEmail(), request.getBrokerName());
        if (assetManagementDetailsOpt.isPresent()) {
            String existingId = assetManagementDetailsOpt.get().getId();
            AssetManagementDetails assetManagementDetails = TJsonMapper.safeCopy(request, AssetManagementDetails.class);
            assetManagementDetails.setEmail(userMail.getEmail());
            assetManagementDetails.setId(existingId);
            assetManagementRepository.save(assetManagementDetails);
            return;
        }
        AssetManagementDetails assetManagementDetails = TJsonMapper.safeCopy(request, AssetManagementDetails.class);
        assetManagementDetails.setEmail(userMail.getEmail());
        assetManagementRepository.save(assetManagementDetails);
    }

    public List<AssetManagementDetails> getAssetManagementDetails(UserMail userMail) {
        return assetManagementRepository.findByEmail(userMail.getEmail());
    }
}
