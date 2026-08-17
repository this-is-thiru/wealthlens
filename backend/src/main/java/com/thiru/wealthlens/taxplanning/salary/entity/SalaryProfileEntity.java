package com.thiru.wealthlens.taxplanning.salary.entity;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.enums.CityTier;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.ProfileType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(value = "salary_profiles")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class SalaryProfileEntity implements AuditableEntity {

    @Id
    private String id;

    @Field("email")
    private String email;

    @Field("profile_name")
    private String profileName;

    @Field(name = "profile_type", targetType = FieldType.STRING)
    private ProfileType profileType;

    @Field("employer_name")
    private String employerName;

    @Field("tax_year")
    private String taxYear;

    @Field(name = "city_tier", targetType = FieldType.STRING)
    private CityTier cityTier;

    @Field("city_name")
    private String cityName;

    @Field(name = "regime_type", targetType = FieldType.STRING)
    private RegimeType regimeType;

    @Field("components")
    private List<SalaryComponentEntity> components;

    @Field(name = "employer_type", targetType = FieldType.STRING)
    private EmployerType employerType;

    @Field("car_provided")
    private Boolean carProvided;

    @Field(name = "car_ownership", targetType = FieldType.STRING)
    private com.thiru.wealthlens.taxplanning.enums.CarOwnership carOwnership;

    @Field(name = "car_engine_size", targetType = FieldType.STRING)
    private com.thiru.wealthlens.taxplanning.enums.CarEngineSize carEngineSize;

    @Field("driver_provided_by_employer")
    private Boolean driverProvidedByEmployer;

    @Field("driver_salary_monthly")
    private Long driverSalaryMonthly;

    @Field("investment_80c")
    private Long investment80c;

    @Field("investment_80d")
    private Long investment80d;

    @Field("home_loan_interest")
    private Long homeLoanInterest;

    @Field("nps_self_80ccd1b")
    private Long npsSelf80ccd1b;

    @Field("monthly_rent_paid")
    private Long monthlyRentPaid;

    @Field("is_paying_rent")
    private Boolean isPayingRent;

    @Field("is_metro_city")
    private Boolean isMetroCity;

    @Field("number_of_children")
    private Integer numberOfChildren;

    @Field("is_physically_disabled")
    private Boolean isPhysicallyDisabled;

    @Field("lta_block_start")
    private String ltaBlockStart;

    @Field("lta_trips_claimed_in_block")
    private Integer ltaTripsClaimedInBlock;

    @Field("lta_carry_forward_available")
    private Boolean ltaCarryForwardAvailable;

    @Field("esop_present")
    private Boolean esopPresent;

    @Field("source_text")
    private String sourceText;

    @Field("parsed_by_ai")
    private Boolean parsedByAi;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
