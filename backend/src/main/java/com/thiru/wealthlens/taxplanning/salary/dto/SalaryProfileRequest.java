package com.thiru.wealthlens.taxplanning.salary.dto;

import com.thiru.wealthlens.taxplanning.enums.CityTier;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.ProfileType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SalaryProfileRequest {

    private String email;
    private String profileName;
    private ProfileType profileType;
    private String employerName;
    private String taxYear;
    private CityTier cityTier;
    private String cityName;
    private RegimeType regimeType;
    private List<SalaryComponentDto> components;
    private EmployerType employerType;
    private Boolean carProvided;
    private com.thiru.wealthlens.taxplanning.enums.CarOwnership carOwnership;
    private com.thiru.wealthlens.taxplanning.enums.CarEngineSize carEngineSize;
    private Boolean driverProvidedByEmployer;
    private Long driverSalaryMonthly;
    private Long investment80c;
    private Long investment80d;
    private Long homeLoanInterest;
    private Long npsSelf80ccd1b;
    private Long monthlyRentPaid;
    private Boolean isPayingRent;
    private Integer numberOfChildren;
    private Boolean isPhysicallyDisabled;
    private String ltaBlockStart;
    private Integer ltaTripsClaimedInBlock;
    private Boolean ltaCarryForwardAvailable;
}
