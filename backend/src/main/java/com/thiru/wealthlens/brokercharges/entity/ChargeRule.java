package com.thiru.wealthlens.brokercharges.entity;

import com.thiru.wealthlens.brokercharges.dto.enums.AggregatorType;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.DedupeScope;
import com.thiru.wealthlens.brokercharges.dto.enums.RoundingPolicy;
import com.thiru.wealthlens.brokercharges.dto.enums.SlabBandBasis;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * One charge within a rate card.
 *
 * <p>This is the unit of extensibility. Adding a charge, or repricing one, is a new or edited rule
 * document — no Java changes. Rules are embedded in {@code ChargeScheduleEntity} (the broker's rate
 * card) and in {@code ChargeInstrumentEntity} (scheme-level charges such as exit load); both sources
 * merge into a single ordered evaluation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChargeRule {

    /** Identifier such as {@code STT} or {@code GST}. Unique within a schedule; must exist in the catalogue. */
    @Field("code")
    private String code;

    /** Label as it should appear on a contract note. */
    @Field("display_name")
    private String displayName;

    @Field(name = "category", targetType = FieldType.STRING)
    private ChargeCategory category;

    @Field(name = "basis", targetType = FieldType.STRING)
    private ChargeBasis basis;

    /** Set by the engine from where the rule was read; not authored in seed data. */
    @Field(name = "source", targetType = FieldType.STRING)
    private ChargeRuleSource source;

    @Field(name = "side", targetType = FieldType.STRING)
    private ChargeSide side;

    /** The occasions on which this rule applies. */
    @Field(name = "events", targetType = FieldType.STRING)
    private Set<ChargeEvent> events;

    /** Which context amount a percentage applies to. Defaults to turnover. */
    @Field(name = "amount_basis", targetType = FieldType.STRING)
    private AmountBasis amountBasis;

    /** Percentage, for {@code TURNOVER} and {@code DERIVED} bases. */
    @Field("rate")
    private Double rate;

    /** Fixed amount, for {@code FLAT} and {@code SCOPED_FLAT} bases. */
    @Field("flat_amount")
    private Double flatAmount;

    /** Amount per share or per lot, for the {@code PER_UNIT} basis. */
    @Field("per_unit_amount")
    private Double perUnitAmount;

    @Field("slabs")
    private List<ChargeSlab> slabs;

    /** Which quantity the slabs band over. Defaults to turnover. */
    @Field(name = "slab_band_basis", targetType = FieldType.STRING)
    private SlabBandBasis slabBandBasis;

    /**
     * For the {@code DERIVED} basis, the charge codes whose amounts form the base.
     *
     * <p>Naming the base explicitly is what keeps GST off securities transaction tax and stamp duty,
     * neither of which is taxable.
     */
    @Field("base_codes")
    private List<String> baseCodes;

    /** SpEL expression producing the amount, for the {@code FORMULA} basis. */
    @Field("formula")
    private String formula;

    /** Optional SpEL predicate. When present and false, the rule contributes nothing. */
    @Field("eligibility")
    private String eligibility;

    @Field("min_amount")
    private Double minAmount;

    @Field("max_amount")
    private Double maxAmount;

    /** Required when both a rate and a flat amount are present; rejected otherwise. */
    @Field(name = "aggregator", targetType = FieldType.STRING)
    private AggregatorType aggregator;

    /** Window within which the charge is levied at most once. Defaults to none. */
    @Field(name = "dedupe_scope", targetType = FieldType.STRING)
    private DedupeScope dedupeScope;

    /** Applied once, by the engine, after every other modifier. */
    @Field(name = "rounding", targetType = FieldType.STRING)
    private RoundingPolicy rounding;

    /**
     * Evaluate once per FIFO lot and sum, rather than once per transaction.
     *
     * <p>Required by holding-period-dependent charges: a redemption spanning lots of different ages
     * attracts a different exit load on each, and averaging can be wrong by the entire charge.
     */
    @Field("per_lot")
    private boolean perLot;

    /**
     * Whether the rule applies to a transaction arising from a corporate action.
     *
     * <p>Defaults to false, so bonus shares and split allotments — issued free — attract nothing
     * unless a rule opts in. Buyback tenders and rights subscriptions are the cases that do.
     */
    @Field("applies_to_corporate_actions")
    private boolean appliesToCorporateActions;

    /** Ascending evaluation order. A derived rule must sort after every code in its base. */
    @Field("order")
    private int order;

    /** Whether the emitted line is itself taxable. Declared per rule, never inferred from category. */
    @Field("taxable")
    private boolean taxable;

    /** Allows a rule to be disabled without removing it from the card. */
    @Field("active")
    private boolean active;

    /** Free text: why this rate, which statute, which broker page it came from. */
    @Field("notes")
    private String notes;
}
