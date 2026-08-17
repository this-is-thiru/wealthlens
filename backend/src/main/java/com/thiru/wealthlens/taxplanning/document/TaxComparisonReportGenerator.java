package com.thiru.wealthlens.taxplanning.document;

import com.thiru.wealthlens.taxplanning.recommendation.RestructuringResult;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaxComparisonReportGenerator {

    private final DocumentGenerationService docService;

    public byte[] generate(TaxComputationEntity computation, SalaryProfileEntity profile, RestructuringResult restructuring) {
        return docService.generateTaxComparisonReport(computation, profile, restructuring);
    }
}
