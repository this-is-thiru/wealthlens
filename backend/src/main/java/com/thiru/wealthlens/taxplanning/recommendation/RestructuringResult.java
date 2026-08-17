package com.thiru.wealthlens.taxplanning.recommendation;

import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileResponse;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RestructuringResult {

    SalaryProfileResponse originalProfile;
    SalaryProfileResponse restructuredProfile;
    List<AllowanceRecommendation> recommendations;
    long originalNewRegimeTax;
    long originalOldRegimeTax;
    long restructuredNewRegimeTax;
    long restructuredOldRegimeTax;
    long totalOptimizedSaving;
    RegimeAdvice regimeAdvice;
}
