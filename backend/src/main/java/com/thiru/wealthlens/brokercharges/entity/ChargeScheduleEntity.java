package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
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
 * A broker's rate card, valid over a date range for a scope.
 *
 * <p>Scope dimensions left null match anything; the resolver ranks candidates by specificity, so a
 * card naming a segment beats one that does not.
 *
 * <h2>Validity and status are separate concerns</h2>
 * Which card applies to a transaction is decided by {@code startDate} and {@code endDate} alone.
 * {@code status} says whether the record is legitimate data — {@code INACTIVE} means it was entered
 * in error and must not be used for any date.
 *
 * <p>Superseding a card sets {@code endDate} and <strong>never</strong> touches {@code status}.
 * Deactivating a superseded card would make a transaction backdated into its window resolve nothing
 * and silently accrue no charge, which is exactly what happens when a past quarter is uploaded long
 * after the rates changed. Whether a card is the current one is expressed by a null {@code endDate}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "charge_schedules")
public class ChargeScheduleEntity implements AuditableEntity {

    @JsonIgnore
    @MongoId
    private String id;

    /** Human-readable key, for example {@code ZERODHA_EQ_DELIVERY_2025_04}. */
    @Indexed(unique = true)
    @Field("schedule_code")
    private String scheduleCode;

    @Indexed
    @Field(name = "broker_name", targetType = FieldType.STRING)
    private BrokerName brokerName;

    /** Null matches any asset type. */
    @Field(name = "asset_type", targetType = FieldType.STRING)
    private AssetType assetType;

    /** Null matches any segment. */
    @Field(name = "segment", targetType = FieldType.STRING)
    private TradeSegment segment;

    /** Null matches any exchange. NSE and BSE levy different transaction charges. */
    @Field("exchange")
    private String exchange;

    /** Null matches any plan. Reserved for per-user negotiated rates. */
    @Field("plan_code")
    private String planCode;

    /** Inclusive. */
    @Field("start_date")
    private LocalDate startDate;

    /** Inclusive. Null means open-ended, and marks this as the current card for its scope. */
    @Field("end_date")
    private LocalDate endDate;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("currency")
    private String currency;

    /**
     * Whether a trade under this card is expected to have an instrument profile.
     *
     * <p>Set on mutual fund cards, whose schemes carry their own charges. When true and no profile
     * resolves, the computation records {@code NO_INSTRUMENT_PROFILE} and appears in the gaps
     * report — a missing profile silently disables the statutory charge whose eligibility reads
     * {@code equityOriented}, so the gap has to be visible rather than merely logged.
     */
    @Field("requires_instrument_profile")
    private boolean requiresInstrumentProfile;

    @Field("rules")
    private List<ChargeRule> rules = new ArrayList<>();

    /** The broker's published charges page, so a human can re-verify the rates. */
    @Field("source_url")
    private String sourceUrl;

    /** When a human last checked these rates. Null means unverified, and is warned about at startup. */
    @Field("verified_on")
    private LocalDate verifiedOn;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
