package com.thiru.wealthlens.taxplanning.recommendation;

import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HrLetterContentBuilder {

    public String buildLetter(SalaryProfileEntity profile, List<AllowanceRecommendation> recommendations) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subject: Request for Salary Restructuring — Tax-Efficient CTC\n\n");
        sb.append("Dear HR / Payroll Team,\n\n");
        sb.append("I would like to restructure my CTC to optimise my salary for Tax Year ")
          .append(profile.getTaxYear())
          .append(" under the Income Tax Act 2025 and IT Rules 2026.\n\n");
        sb.append("PROPOSED RESTRUCTURING:\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        for (int i = 0; i < recommendations.size(); i++) {
            AllowanceRecommendation rec = recommendations.get(i);
            sb.append(i + 1).append(". ").append(rec.getDisplayName())
              .append(" — ₹").append(rec.getSuggestedAnnualAmount()).append("/year\n");
            sb.append("   Legal basis: ").append(rec.getItSection()).append("\n");
            sb.append("   Action: ").append(rec.getActionRequired()).append("\n");
            if (rec.getDocumentsRequired() != null && !rec.getDocumentsRequired().isEmpty()) {
                sb.append("   Documents: ").append(String.join(", ", rec.getDocumentsRequired())).append("\n");
            }
            sb.append("\n");
        }

        long totalSaving = recommendations.stream()
                .mapToLong(AllowanceRecommendation::getEstimatedTaxSaving)
                .sum();
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("Estimated total additional take-home: ₹").append(totalSaving).append("/year\n");
        sb.append("Please let me know what forms or approvals are required.\n\n");
        sb.append("Regards,\n").append(profile.getEmail());

        return sb.toString();
    }
}
