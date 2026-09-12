package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Turns a charge breakdown into the part of it that reduces a capital gain (decision D3).
 *
 * <p>Not every charge does. STT is expressly disallowed and is the largest single charge on a
 * delivery sell — roughly ₹100 on a ₹1,00,000 disposal against ₹20 of brokerage — so counting it
 * inflates the cost base by an amount that matters. AMC and account opening are account-level and
 * not incurred in connection with any particular transfer.
 *
 * <p>The catalogue is the authority, so adding a charge stays a data change.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class ChargeDeductibilityService {

    private final ChargeCatalogueRepository chargeCatalogueRepository;

    /**
     * The deductible part of a breakdown.
     *
     * <p>A code the catalogue does not carry is <b>excluded and logged</b>, never assumed
     * deductible: an unknown code silently reducing a filed gain is the failure this whole
     * mechanism exists to prevent. The seeder already refuses a code that does not declare the
     * flag, so reaching this branch means the catalogue and the recorded charge have diverged.
     */
    public double deductibleTotal(Map<String, Double> amountByCode) {
        if (amountByCode == null || amountByCode.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (Map.Entry<String, Double> entry : amountByCode.entrySet()) {
            if (isDeductible(entry.getKey())) {
                total += entry.getValue() == null ? 0.0 : entry.getValue();
            }
        }
        return total;
    }

    private boolean isDeductible(String code) {
        Optional<ChargeCatalogueEntity> entry = chargeCatalogueRepository.findByCode(code);
        if (entry.isEmpty()) {
            log.warn("Charge code {} is not in the catalogue, so it cannot be shown to be deductible;"
                    + " excluding it from the deductible cost", code);
            return false;
        }
        Boolean deductible = entry.get().getDeductibleForCapitalGains();
        if (deductible == null) {
            // Distinct from the branch above, and the distinction matters operationally: the code
            // IS catalogued, it just predates the flag. Reporting it as absent from the catalogue
            // sends whoever reads this log looking in the wrong place.
            log.warn("Charge code {} is catalogued but does not declare deductibleForCapitalGains,"
                    + " so it is excluded from the deductible cost. Re-run POST /charges/seed to"
                    + " backfill it", code);
            return false;
        }
        if (!deductible) {
            log.debug("Charge code {} is not deductible against capital gains", code);
        }
        return deductible;
    }
}
