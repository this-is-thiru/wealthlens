package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.dto.request.BrokerChargesRequest;
import com.thiru.wealthlens.brokercharges.entity.BrokerCharges;
import com.thiru.wealthlens.brokercharges.repository.BrokerChargesRepository;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.model.BrokerageCharges;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * <b>Superseded.</b> Rate-card management for the superseded implementation.
 *
 * <p>Replaced by {@code ChargeScheduleService}.
 *
 * <p><b>Do not delete yet.</b> Still reached by live code, and removal waits on human testing of
 * the charges engine — see {@code docs/charges-engine/implementation-checklist.md}, Chunk 11.
 *
 * <p>Plain {@code @Deprecated} rather than {@code forRemoval = true} on purpose. A removal warning
 * is <em>not</em> suppressed at a deprecated use site, so seventeen interlinked classes would warn
 * about each other and bury the only signal worth having. An ordinary deprecation warning is
 * suppressed inside deprecated code, which leaves the build reporting exactly the <b>live</b>
 * callers still to be migrated — and that list reaching zero is the precondition for deleting any
 * of this.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class BrokerChargeService {

    private static final int BROKER_CHARGES_VALIDITY_IN_YEARS = 100;

    private final BrokerChargesRepository brokerChargesRepository;

    public String addBrokerCharge(BrokerChargesRequest brokerChargesRequest) {
        BrokerName brokerName = brokerChargesRequest.getBrokerName();
        LocalDate fromDate = brokerChargesRequest.getStartDate();

        Optional<BrokerCharges> chargesOptional = brokerChargesRepository.findActiveBrokerChargesOnDate(brokerName, fromDate);
        if (chargesOptional.isPresent()) {
            throw new BadRequestException("Broker charges already exist for the given broker name and date range with id: " + chargesOptional.get().getId());
        }

        BrokerCharges brokerCharges = toEntity(brokerChargesRequest);
        brokerChargesRepository.save(brokerCharges);

        return "Broker charges added successfully with id: " + brokerCharges.getId();
    }

    public BrokerCharges getBrokerCharge(BrokerName brokerName, LocalDate fromDate) {
        Optional<BrokerCharges> chargesOptional = brokerChargesRepository.findActiveBrokerChargesOnDate(brokerName, fromDate);
        return chargesOptional.orElse(null);
    }

    public BrokerCharges getBrokerCharges(String id) {
        Optional<BrokerCharges> chargesOptional = brokerChargesRepository.findById(id);
        if (chargesOptional.isEmpty()) {
            throw new BadRequestException("Broker charges with id: " + id + " not found");
        }
        return chargesOptional.get();
    }

    public String changeEndDate(String id, LocalDate endDate) {
        Optional<BrokerCharges> chargesOptional = brokerChargesRepository.findById(id);
        if (chargesOptional.isEmpty()) {
            throw new BadRequestException("Broker charges with id: " + id + " not found");
        }

        BrokerCharges charges = chargesOptional.get();
        charges.setEndDate(endDate);
        brokerChargesRepository.save(charges);
        return "Broker charges updated successfully with id: " + id;
    }

    private static BrokerCharges toEntity(BrokerChargesRequest brokerChargesRequest) {
        BrokerCharges brokerCharges = new BrokerCharges();

        brokerCharges.setBrokerName(brokerChargesRequest.getBrokerName());
        brokerCharges.setStartDate(brokerChargesRequest.getStartDate());
        brokerCharges.setEndDate(brokerChargesRequest.getStartDate().plusYears(BROKER_CHARGES_VALIDITY_IN_YEARS));
        brokerCharges.setStatus(brokerChargesRequest.getStatus());
        brokerCharges.setAccountOpeningCharges(brokerChargesRequest.getAccountOpeningCharges());
        brokerCharges.setAmcChargesAnnually(brokerChargesRequest.getAmcChargesAnnually());
        brokerCharges.setAmcChargeFrequency(brokerChargesRequest.getAmcChargesFrequency());
        brokerCharges.setBrokerageCharges(TJsonMapper.copy(brokerChargesRequest.getBrokerageCharges(), BrokerageCharges.class));
        brokerCharges.setDpChargesPerScrip(brokerChargesRequest.getDpChargesPerScrip());
        brokerCharges.setStt(brokerChargesRequest.getStt());
        brokerCharges.setGstApplicableDescription(brokerChargesRequest.getGstApplicableDescription());
        brokerCharges.setSebiCharges(brokerChargesRequest.getSebiCharges());
        brokerCharges.setStampDuty(brokerChargesRequest.getStampDuty());

        return brokerCharges;
    }

}
