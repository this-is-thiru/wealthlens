package com.thiru.wealthlens.taxplanning.salary.dto;

import com.thiru.wealthlens.taxplanning.enums.CarEngineSize;
import com.thiru.wealthlens.taxplanning.enums.CarOwnership;
import com.thiru.wealthlens.taxplanning.enums.CityTier;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.ProfileType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryComponentEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryProfileResponse {

    private String id;
    private String email;
    private String profileName;
    private ProfileType profileType;
    private String employerName;
    private String taxYear;
    private CityTier cityTier;
    private String cityName;
    private RegimeType regimeType;
    private EmployerType employerType;
    private List<SalaryComponentDto> components;
    private Boolean carProvided;
    private CarOwnership carOwnership;
    private CarEngineSize carEngineSize;
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
    private Boolean isMetroCity;
    private Long annualCtc;
    private String computedAt;

    private static final List<String> METRO_CITIES = Arrays.asList(
            "Mumbai", "Delhi", "Kolkata", "Chennai",
            "Hyderabad", "Bengaluru", "Pune", "Ahmedabad"
    );

    public static SalaryProfileResponse fromEntity(SalaryProfileEntity entity) {
        List<SalaryComponentDto> componentDtos = entity.getComponents() == null
                ? List.of()
                : entity.getComponents().stream()
                        .map(SalaryProfileResponse::toComponentDto)
                        .collect(Collectors.toList());

        long annualCtc = componentDtos.stream()
                .mapToLong(c -> c.getAnnualAmount() != null ? c.getAnnualAmount() : 0L)
                .sum();

        boolean isMetroCity = deriveIsMetroCity(entity.getCityName());

        return SalaryProfileResponse.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .profileName(entity.getProfileName())
                .profileType(entity.getProfileType())
                .employerName(entity.getEmployerName())
                .taxYear(entity.getTaxYear())
                .cityTier(entity.getCityTier())
                .cityName(entity.getCityName())
                .regimeType(entity.getRegimeType())
                .employerType(entity.getEmployerType())
                .components(componentDtos)
                .carProvided(entity.getCarProvided())
                .carOwnership(entity.getCarOwnership())
                .carEngineSize(entity.getCarEngineSize())
                .driverProvidedByEmployer(entity.getDriverProvidedByEmployer())
                .driverSalaryMonthly(entity.getDriverSalaryMonthly())
                .investment80c(entity.getInvestment80c())
                .investment80d(entity.getInvestment80d())
                .homeLoanInterest(entity.getHomeLoanInterest())
                .npsSelf80ccd1b(entity.getNpsSelf80ccd1b())
                .monthlyRentPaid(entity.getMonthlyRentPaid())
                .isPayingRent(entity.getIsPayingRent())
                .numberOfChildren(entity.getNumberOfChildren())
                .isPhysicallyDisabled(entity.getIsPhysicallyDisabled())
                .isMetroCity(isMetroCity)
                .annualCtc(annualCtc)
                .build();
    }

    private static SalaryComponentDto toComponentDto(SalaryComponentEntity entity) {
        SalaryComponentDto dto = new SalaryComponentDto();
        dto.setAllowanceCode(entity.getAllowanceCode());
        dto.setAnnualAmount(entity.getAnnualAmount());
        dto.setIsCurrent(entity.getIsCurrent());
        dto.setNotes(entity.getNotes());
        return dto;
    }

    public static boolean deriveIsMetroCity(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            return false;
        }
        return METRO_CITIES.stream()
                .anyMatch(metro -> metro.equalsIgnoreCase(cityName.trim()));
    }
}
