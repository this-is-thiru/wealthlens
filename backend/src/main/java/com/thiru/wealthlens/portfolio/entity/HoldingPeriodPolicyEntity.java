package com.thiru.wealthlens.portfolio.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodDate;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodOutcome;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * One holding-period rule, stored rather than compiled.
 *
 * <p>Persisted for the same reason a rate card is. These rules changed in 2023 and again in 2024,
 * and the analysis behind them records genuine uncertainty about one commencement date — so a
 * correction has to be a data change rather than a release. Holding them in Java would mean the one
 * rule most likely to need amending is the one that needs a deployment to amend.
 *
 * <p>Validity-windowed and never edited once deployed, exactly as ADR-26 requires of a rate card: a
 * trade must be classified by the rule in force when it happened, so a change ships as a new row
 * whose window opens where the old one closes. Editing a row would silently reclassify history.
 */
@Document(value = "holding_period_policies")
@CompoundIndex(name = "holding_policy_code_idx", def = "{'policy_code': 1}", unique = true)
@CompoundIndex(name = "holding_policy_lookup_idx", def = "{'asset_type': 1, 'sub_class': 1, 'effective_from': 1}")
@NoArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
public class HoldingPeriodPolicyEntity implements AuditableEntity {

    @JsonIgnore
    @MongoId
    @Setter(AccessLevel.NONE)
    private String id;

    /** Quotable, and recorded on a classification so a tax figure can be traced to its rule. */
    @Field("policy_code")
    private String policyCode;

    @Field(name = "asset_type", targetType = FieldType.STRING)
    private AssetType assetType;

    /**
     * The second dimension, where the asset type alone does not decide the rule —
     * {@code EQUITY_ORIENTED} / {@code SPECIFIED} / {@code OTHER} for a fund, {@code LISTED} /
     * {@code UNLISTED} for a bond. Null matches any.
     */
    @Field("sub_class")
    private String subClass;

    /**
     * Which date the validity window is measured against.
     *
     * <p>The distinction most easily got wrong: the 12/24-month framework applies by date of
     * transfer, while the specified-fund rule applies by date of acquisition. A policy store that
     * assumed one would misclassify every trade straddling a change.
     */
    @Field(name = "keyed_on", targetType = FieldType.STRING)
    private HoldingPeriodDate keyedOn;

    @Field("effective_from")
    private LocalDate effectiveFrom;

    /** Null means open-ended — the rule still in force. */
    @Field("effective_to")
    private LocalDate effectiveTo;

    @Field(name = "outcome", targetType = FieldType.STRING)
    private HoldingPeriodOutcome outcome;

    /** Only meaningful when the outcome is {@code LONG_TERM_AFTER_MONTHS}. */
    @Field("months")
    private Integer months;

    /** Why this rule exists, in words, so a classification can explain itself. */
    @Field("note")
    private String note;

    @Field("audit_metadata")
    @Setter(AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
