package com.thiru.wealthlens.taxplanning.parsing;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ParsedCtcDto {

    private String employerName;
    private long totalCtcAnnual;
    private long basicAnnual;
    private long hraAnnual;
    private long ltaAnnual;
    private long specialAllowanceAnnual;
    private long mealVoucherAnnual;
    private long telephoneAnnual;
    private long fuelAllowanceAnnual;
    private long driverAllowanceAnnual;
    private long medicalReimbursementAnnual;
    private long booksPeriodicalsAnnual;
    private long uniformAllowanceAnnual;
    private long childrenEducationAnnual;
    private long hostelAllowanceAnnual;
    private long giftVoucherAnnual;
    private long npsEmployerAnnual;
    private long pfEmployerAnnual;
    private long gratuityAnnual;
    private long performanceBonusAnnual;
    private long variablePayAnnual;
    private long otherAllowancesAnnual;

    private String cityName;

    private boolean carProvided;
    private boolean carEngineAbove1600cc;
    private boolean driverProvided;

    private boolean esopPresent;
    private String esopDetails;

    private String confidence;
    private List<String> unmappedComponents;
    private String parsingNotes;
}
