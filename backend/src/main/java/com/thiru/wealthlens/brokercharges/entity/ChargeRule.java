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
    private String code;

    /** Label as it should appear on a contract note. */
    private String displayName;

    private ChargeCategory category;

    private ChargeBasis basis;

    /** Set by the engine from where the rule was read; not authored in seed data. */
    private ChargeRuleSource source;

    private ChargeSide side;

    /** The occasions on which this rule applies. */
    private Set<ChargeEvent> events;

    /** Which context amount a percentage applies to. Defaults to turnover. */
    private AmountBasis amountBasis;

    /** Percentage, for {@code TURNOVER} and {@code DERIVED} bases. */
    private Double rate;

    /** Fixed amount, for {@code FLAT} and {@code SCOPED_FLAT} bases. */
    private Double flatAmount;

    /** Amount per share or per lot, for the {@code PER_UNIT} basis. */
    private Double perUnitAmount;

    private List<ChargeSlab> slabs;

    /** Which quantity the slabs band over. Defaults to turnover. */
    private SlabBandBasis slabBandBasis;

    /**
     * For the {@code DERIVED} basis, the charge codes whose amounts form the base.
     *
     * <p>Naming the base explicitly is what keeps GST off securities transaction tax and stamp duty,
     * neither of which is taxable.
     */
    private List<String> baseCodes;

    /** SpEL expression producing the amount, for the {@code FORMULA} basis. */
    private String formula;

    /** Optional SpEL predicate. When present and false, the rule contributes nothing. */
    private String eligibility;

    private Double minAmount;

    private Double maxAmount;

    /** Required when both a rate and a flat amount are present; rejected otherwise. */
    private AggregatorType aggregator;

    /** Window within which the charge is levied at most once. Defaults to none. */
    private DedupeScope dedupeScope;

    /** Applied once, by the engine, after every other modifier. */
    private RoundingPolicy rounding;

    /**
     * Evaluate once per FIFO lot and sum, rather than once per transaction.
     *
     * <p>Required by holding-period-dependent charges: a redemption spanning lots of different ages
     * attracts a different exit load on each, and averaging can be wrong by the entire charge.
     */
    private boolean perLot;

    /**
     * Whether the rule applies to a transaction arising from a corporate action.
     *
     * <p>Defaults to false, so bonus shares and split allotments — issued free — attract nothing
     * unless a rule opts in. Buyback tenders and rights subscriptions are the cases that do.
     */
    private boolean appliesToCorporateActions;

    /** Ascending evaluation order. A derived rule must sort after every code in its base. */
    private int order;

    /** Whether the emitted line is itself taxable. Declared per rule, never inferred from category. */
    private boolean taxable;

    /** Allows a rule to be disabled without removing it from the card. */
    private boolean active;

    /** Free text: why this rate, which statute, which broker page it came from. */
    private String notes;

    /**
     * The amount a percentage applies to, with the default resolved.
     *
     * <p>Lives here rather than in the engine because the engine is not the only reader: every
     * calculator that prices a percentage asks the same question, and a default re-derived in eight
     * places is a default that will eventually disagree with itself. A rule omitting the field is
     * priced on turnover, which is what every cash-segment charge means.
     */
    public AmountBasis effectiveAmountBasis() {
        return amountBasis == null ? AmountBasis.TURNOVER : amountBasis;
    }

    /**
     * The quantity the slabs are banded over, with the default resolved.
     *
     * <p>Not the same question as {@link #effectiveAmountBasis()}, and conflating the two is the
     * trap a tiered charge sets: an exit load banded by holding period charges a percentage of the
     * redemption, not of the number of days.
     */
    public SlabBandBasis effectiveSlabBandBasis() {
        return slabBandBasis == null ? SlabBandBasis.TURNOVER : slabBandBasis;
    }
}
