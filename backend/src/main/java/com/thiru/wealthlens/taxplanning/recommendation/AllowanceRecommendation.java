package com.thiru.wealthlens.taxplanning.recommendation;

import com.thiru.wealthlens.taxplanning.enums.AvailabilityPath;
import com.thiru.wealthlens.taxplanning.enums.HrSupportLikelihood;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
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
    ItrPortalPath itrPortalPath;
    List<String> documentsRequired;
    List<String> documentsToKeep;
    String itSection;
    List<String> eligibilityConditions;
    List<String> commonMistakes;
    List<FaqEntry> beginnerFaq;

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    public static class ItrPortalPath {
        private Integer stepNumber;
        private String sectionName;
        private String fieldName;
        private String howToFill;
    }

    @Getter @Setter @ToString @EqualsAndHashCode
    public static class FaqEntry {
        private String question;
        private String answer;
    }
}
