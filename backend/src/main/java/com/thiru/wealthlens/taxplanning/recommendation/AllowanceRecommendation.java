package com.thiru.wealthlens.taxplanning.recommendation;

import com.thiru.wealthlens.taxplanning.enums.AvailabilityPath;
import com.thiru.wealthlens.taxplanning.enums.HrSupportLikelihood;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AllowanceRecommendation {

    String allowanceCode;
    String displayName;
    String description;
    String whyItMatters;
    long suggestedAnnualAmount;
    long estimatedTaxSaving;
    int priority;
    AvailabilityPath availabilityPath;
    HrSupportLikelihood hrSupportLikelihood;
    String actionRequired;
    String hrAskTemplate;
    String whatIfHrSaysNo;
    AllowanceCatalogueEntity.ItrPortalPath itrPortalPath;
    List<String> documentsRequired;
    List<String> documentsToKeep;
    String itSection;
    List<String> eligibilityConditions;
    List<String> commonMistakes;
    List<AllowanceCatalogueEntity.FaqEntry> beginnerFaq;
}
