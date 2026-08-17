package com.thiru.wealthlens.brokercharges.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.model.BrokerageCharges;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "broker_charges")
public class BrokerCharges implements AuditableEntity {
    @JsonIgnore
    @MongoId
    private String id;

    @Field(name = "broker_name", targetType = FieldType.STRING)
    private BrokerName brokerName;

    @Field("start_date")
    private LocalDate startDate;

    @Field("end_date")
    private LocalDate endDate;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("account_opening_charges")
    private double accountOpeningCharges;

    @Field("amc_charges")
    private double amcChargesAnnually;

    @Field(name = "amc_charges_frequency", targetType = FieldType.STRING)
    private AmcChargeFrequency amcChargeFrequency;

    @Field("brokerage_charges")
    private BrokerageCharges brokerageCharges;

    @Field("dp_charges_per_scrip")
    private double dpChargesPerScrip;

    @Field("stt")
    private double stt;

    /**
     * Example of gst applicable description
     * 18%-brokerage,18%-dp_charges,18%-stt,18%-amc_charges,18%-dp_charges_per_scrip
     */
    @Field("gst")
    private String gstApplicableDescription;

    @Field("sebi_charges")
    private double sebiCharges;

    @Field("stamp_duty")
    private double stampDuty;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
