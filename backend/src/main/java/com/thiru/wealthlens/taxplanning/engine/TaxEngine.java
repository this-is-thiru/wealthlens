package com.thiru.wealthlens.taxplanning.engine;

import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import java.util.List;

public interface TaxEngine {

    RegimeType getRegime();

    String getSupportedTaxYear();

    TaxComputationEntity.TaxResult compute(
            SalaryProfileEntity profile,
            TaxSlabPolicyEntity slabPolicy,
            PerquisitePolicyEntity perquisitePolicy,
            List<AllowanceCatalogueEntity> allowanceCatalogue,
            FormulaEvaluator formulaEvaluator
    );
}
