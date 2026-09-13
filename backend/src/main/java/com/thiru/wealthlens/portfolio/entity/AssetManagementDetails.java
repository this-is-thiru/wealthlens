package com.thiru.wealthlens.portfolio.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Document(value = "asset_management_details")
public class AssetManagementDetails implements AuditableEntity {
    @JsonIgnore
    @MongoId
    private String id;

    @Field("email")
    private String email;

    @Field("demat_account_id")
    private String dematAccountId;

    @Field(name = "broker_name", targetType = FieldType.STRING)
    private BrokerName brokerName;

    @Field("account_opening_charges")
    private double accountOpeningCharges;

    @Field("tax_on_account_opening_charges")
    private double taxOnAccountOpeningCharges;

    @Field("last_amc_charges_deducted_on")
    private LocalDate lastAmcChargesDeductedOn;

    @Field(name = "amc_charges_frequency", targetType = FieldType.STRING)
    private AmcChargeFrequency amcChargesFrequency;

    @Field("amc_charges_events")
    private List<AmcChargesEvent> amcChargesEvents = new ArrayList<>();

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();

    public record AmcChargesEvent(String userChargesId, LocalDate deductionDate, double deductionAmount, List<LocalDate> datesRange) {
    }
}
