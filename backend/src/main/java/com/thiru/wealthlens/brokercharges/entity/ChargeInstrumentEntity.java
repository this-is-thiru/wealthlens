package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.FundCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.PlanType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * Charges belonging to an instrument rather than to a broker, plus the scheme attributes rules
 * need in order to decide whether they apply.
 *
 * <p>Exit load is the motivating case: two mutual funds bought through the same broker on the same
 * day carry different exit loads, and an index fund may carry none. Holding that on the broker's
 * rate card would mean one card per fund.
 *
 * <p>This is also the only home for {@code equityOriented}. Without it the rule "securities
 * transaction tax applies to equity-oriented funds but not debt funds" cannot be expressed at all.
 *
 * <p>Versioned exactly like {@code ChargeScheduleEntity}: an asset management company revising an
 * exit load closes the previous profile's window, and a redemption backdated into that window uses
 * the load that was in force then.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "charge_instruments")
public class ChargeInstrumentEntity implements AuditableEntity {

    @JsonIgnore
    @MongoId
    private String id;

    /** The identifier transactions actually carry today. */
    @Indexed
    @Field("stock_code")
    private String stockCode;

    /** Stored now so profiles can be re-keyed on it later without a migration. */
    @Field("isin")
    private String isin;

    @Field("name")
    private String name;

    @Field(name = "asset_type", targetType = FieldType.STRING)
    private AssetType assetType;

    @Field(name = "fund_category", targetType = FieldType.STRING)
    private FundCategory fundCategory;

    /**
     * Whether the scheme is equity-oriented for securities transaction tax.
     *
     * <p>Explicit rather than derived from {@code fundCategory}: the determination rests on actual
     * equity allocation, and an index fund, an ELSS and a plain equity fund can all qualify.
     */
    @Field("equity_oriented")
    private Boolean equityOriented;

    /** Direct or regular. Decides whether a distributor transaction fee can apply at all. */
    @Field(name = "plan_type", targetType = FieldType.STRING)
    private PlanType planType;

    @Field("amc")
    private String amc;

    @Field("start_date")
    private LocalDate startDate;

    /** Null means open-ended. See {@link ChargeScheduleEntity} on validity versus status. */
    @Field("end_date")
    private LocalDate endDate;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    /** Scheme-level charges: exit load, and any fee the fund rather than the broker levies. */
    @Field("rules")
    private List<ChargeRule> rules = new ArrayList<>();

    @Field("source_url")
    private String sourceUrl;

    @Field("verified_on")
    private LocalDate verifiedOn;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
