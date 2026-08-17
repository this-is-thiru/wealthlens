package com.thiru.wealthlens.taxplanning.recommendation;

import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class RegimeAdvisor {

    public RegimeAdvice advise(
            TaxComputationEntity.TaxResult newRegimeResult,
            TaxComputationEntity.TaxResult oldRegimeResult,
            SalaryProfileEntity profile
    ) {
        long newTax = newRegimeResult.getTotalTax();
        long oldTax = oldRegimeResult.getTotalTax();
        long savingIfNew = oldTax - newTax;

        RegimeType recommended = savingIfNew >= 0 ? RegimeType.NEW_REGIME : RegimeType.OLD_REGIME;
        long absoluteSaving = Math.abs(savingIfNew);

        List<String> switchTriggers = new ArrayList<>();

        if (recommended == RegimeType.NEW_REGIME) {
            if (profile.getInvestment80c() != null && profile.getInvestment80c() < 150000) {
                long unclaimed80c = 150000 - profile.getInvestment80c();
                long saving80c = Math.round(unclaimed80c * 0.30);
                switchTriggers.add("Maximise 80C by ₹" + unclaimed80c + " → saves ₹" + saving80c + " in old regime");
            }
            if (Boolean.TRUE.equals(profile.getIsPayingRent()) && profile.getMonthlyRentPaid() != null && profile.getMonthlyRentPaid() > 0) {
                switchTriggers.add("HRA exemption available in old regime if salary restructured");
            }
            if (profile.getHomeLoanInterest() != null && profile.getHomeLoanInterest() > 0) {
                switchTriggers.add("Home loan interest ₹" + profile.getHomeLoanInterest() + " deductible only in old regime");
            }
            if (profile.getInvestment80d() != null && profile.getInvestment80d() < 25000) {
                switchTriggers.add("Health insurance premium up to ₹25,000 under 80D — old regime only");
            }
            long breakeven = estimateBreakevenDeductions(profile, newTax, oldTax);
            long currentDeductions = currentTotalDeductions(profile);
            switchTriggers.add("Old regime needs ₹" + breakeven + " deductions to break even — you have ₹" + currentDeductions);
        } else {
            switchTriggers.add("New regime: simpler filing, no investment proofs needed");
            switchTriggers.add("New regime benefits: NPS Employer (80CCD(2)) + Meal Vouchers in both regimes");
        }

        return RegimeAdvice.builder()
                .recommendedRegime(recommended)
                .annualSaving(absoluteSaving)
                .newRegimeTotalTax(newTax)
                .oldRegimeTotalTax(oldTax)
                .switchTriggers(switchTriggers)
                .taxYear(profile.getTaxYear())
                .build();
    }

    private long estimateBreakevenDeductions(SalaryProfileEntity profile, long newTax, long oldTax) {
        if (oldTax <= newTax) {
            return 0;
        }
        long gap = oldTax - newTax;
        return Math.round(gap / 0.30);
    }

    private long currentTotalDeductions(SalaryProfileEntity profile) {
        long total = 0;
        if (profile.getInvestment80c() != null) {
            total += profile.getInvestment80c();
        }
        if (profile.getInvestment80d() != null) {
            total += profile.getInvestment80d();
        }
        if (profile.getNpsSelf80ccd1b() != null) {
            total += profile.getNpsSelf80ccd1b();
        }
        if (profile.getHomeLoanInterest() != null) {
            total += profile.getHomeLoanInterest();
        }
        return total;
    }
}
