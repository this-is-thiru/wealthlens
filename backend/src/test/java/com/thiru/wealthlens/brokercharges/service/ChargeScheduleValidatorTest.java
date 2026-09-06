package com.thiru.wealthlens.brokercharges.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.AggregatorType;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.DedupeScope;
import com.thiru.wealthlens.brokercharges.engine.ChargeFormulaEvaluator;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeSlab;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What makes a rate card safe to accept.
 *
 * <p>Rate cards are data, so the compiler cannot check them and a mistake survives until a trade is
 * priced — by which time it has been applied to a quarter of transactions. Several behaviours
 * elsewhere in the engine are documented as "the validator prevents this"; this is where that
 * promise is kept.
 *
 * <p>Every case asserts the message. An exception type tells whoever is fixing a seed file nothing
 * about which rule is wrong.
 */
@ExtendWith(MockitoExtension.class)
class ChargeScheduleValidatorTest {

    private static final Set<String> CATALOGUE =
            Set.of("BROKERAGE", "GST", "STT", "DP", "SEBI_FEE", "EXIT_LOAD");

    @Mock
    private ChargeCatalogueRepository chargeCatalogueRepository;

    private ChargeScheduleValidator validator;

    @BeforeEach
    void setUp() {
        when(chargeCatalogueRepository.findByStatus(EntityStatus.ACTIVE))
                .thenReturn(CATALOGUE.stream().map(ChargeScheduleValidatorTest::catalogued).toList());
        validator = new ChargeScheduleValidator(chargeCatalogueRepository, new ChargeFormulaEvaluator());
    }

    @Test
    void validate_whenTheCardIsSound_passes() {
        assertThatCode(() -> validator.validate(schedule(brokerage(), gst()))).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- structure

    @Test
    void validate_whenTheCardHasNoRules_isRejected() {
        // Given — an empty card resolves and then prices everything at zero, which is
        // indistinguishable from a broker that charges nothing
        assertThatThrownBy(() -> validator.validate(schedule()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no rules");
    }

    @Test
    void validate_whenTwoRulesShareACode_isRejected() {
        // Given — a derived rule naming the code would silently pick up both
        ChargeRule duplicate = flat("BROKERAGE", 30.0, 20);

        assertThatThrownBy(() -> validator.validate(schedule(brokerage(), duplicate)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BROKERAGE")
                .hasMessageContaining("more than once");
    }

    @Test
    void validate_whenACodeIsNotInTheCatalogue_isRejected() {
        // Given — the catalogue is what stops a typo becoming a charge nobody recognises, and what
        // lets a report label a line
        ChargeRule unknown = flat("BROKERGE", 20.0, 10);

        assertThatThrownBy(() -> validator.validate(schedule(unknown)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BROKERGE")
                .hasMessageContaining("catalogue");
    }

    @Test
    void validate_whenTheEndDatePrecedesTheStartDate_isRejected() {
        ChargeScheduleEntity schedule = schedule(brokerage());
        schedule.setEndDate(LocalDate.of(2025, 3, 1));

        assertThatThrownBy(() -> validator.validate(schedule))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("end date");
    }

    @Test
    void validate_whenThereIsNoStartDate_isRejected() {
        // Given — validity is decided by the date window alone, so a card without one resolves for
        // no trade at all
        ChargeScheduleEntity schedule = schedule(brokerage());
        schedule.setStartDate(null);

        assertThatThrownBy(() -> validator.validate(schedule))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("start date");
    }

    // ---------------------------------------------------------------- basis parameters

    @Test
    void validate_whenATurnoverRuleHasNoRate_isRejected() {
        assertThatThrownBy(() -> validator.validate(schedule(rule("STT", ChargeBasis.TURNOVER, 10))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STT")
                .hasMessageContaining("rate");
    }

    @Test
    void validate_whenAFlatRuleHasNoAmount_isRejected() {
        assertThatThrownBy(() -> validator.validate(schedule(rule("BROKERAGE", ChargeBasis.FLAT, 10))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BROKERAGE")
                .hasMessageContaining("flat amount");
    }

    @Test
    void validate_whenAPerUnitRuleHasNoAmount_isRejected() {
        assertThatThrownBy(() -> validator.validate(schedule(rule("SEBI_FEE", ChargeBasis.PER_UNIT, 10))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SEBI_FEE")
                .hasMessageContaining("per unit amount");
    }

    @Test
    void validate_whenAScopedFlatRuleHasNoAmount_isRejected() {
        ChargeRule rule = rule("DP", ChargeBasis.SCOPED_FLAT, 10);
        rule.setDedupeScope(DedupeScope.PER_SCRIP_PER_DAY);

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DP")
                .hasMessageContaining("flat amount");
    }

    @Test
    void validate_whenAFormulaRuleHasNoExpression_isRejected() {
        assertThatThrownBy(() -> validator.validate(schedule(rule("EXIT_LOAD", ChargeBasis.FORMULA, 10))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("EXIT_LOAD")
                .hasMessageContaining("formula");
    }

    @Test
    void validate_whenARateIsNegative_isRejected() {
        // Given — a charge that pays the user
        ChargeRule rule = rule("STT", ChargeBasis.TURNOVER, 10);
        rule.setRate(-0.1);

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STT")
                .hasMessageContaining("negative");
    }

    // ---------------------------------------------------------------- aggregator (D7)

    @Test
    void validate_whenARuleHasARateAndAFlatAmountWithoutAnAggregator_isRejected() {
        // Given — D7. The superseded implementation resolved this to zero, silently, so a mispriced
        // card looked exactly like a free trade.
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.TURNOVER, 10);
        rule.setRate(0.03);
        rule.setFlatAmount(20.0);

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BROKERAGE")
                .hasMessageContaining("aggregator");
    }

    @Test
    void validate_whenAnAggregatorIsDeclaredWithoutBothOperands_isRejected() {
        // Given — nothing to choose between, so the aggregator states an intent the card does not
        // carry out
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.TURNOVER, 10);
        rule.setRate(0.03);
        rule.setAggregator(AggregatorType.MIN);

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BROKERAGE")
                .hasMessageContaining("aggregator");
    }

    @Test
    void validate_whenARateAndAFlatAmountAreAggregated_passes() {
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.TURNOVER, 10);
        rule.setRate(0.03);
        rule.setFlatAmount(20.0);
        rule.setAggregator(AggregatorType.MIN);

        assertThatCode(() -> validator.validate(schedule(rule))).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- derived ordering

    @Test
    void validate_whenADerivedRuleNamesNoCodes_isRejected() {
        ChargeRule gst = derived("GST", 100, List.of());

        assertThatThrownBy(() -> validator.validate(schedule(brokerage(), gst)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GST")
                .hasMessageContaining("base");
    }

    @Test
    void validate_whenADerivedRuleNamesACodeTheCardDoesNotCarry_isRejected() {
        ChargeRule gst = derived("GST", 100, List.of("BROKERAGE", "DP"));

        assertThatThrownBy(() -> validator.validate(schedule(brokerage(), gst)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GST")
                .hasMessageContaining("DP");
    }

    @Test
    void validate_whenADerivedRuleNamesACodeEvaluatedAfterIt_isRejected() {
        // Given — the accumulator holds only what has already been applied, so this taxes a line
        // that does not exist yet and quietly under-collects
        ChargeRule gst = derived("GST", 5, List.of("BROKERAGE"));

        assertThatThrownBy(() -> validator.validate(schedule(brokerage(), gst)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GST")
                .hasMessageContaining("BROKERAGE")
                .hasMessageContaining("order");
    }

    @Test
    void validate_whenADerivedRuleNamesACodeAtItsOwnOrder_isRejected() {
        // Given — equal order is broken by code, which is deterministic but not something a rate
        // card should be made to depend on
        ChargeRule gst = derived("GST", 10, List.of("BROKERAGE"));

        assertThatThrownBy(() -> validator.validate(schedule(brokerage(), gst)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("order");
    }

    // ---------------------------------------------------------------- slabs

    @Test
    void validate_whenASlabRuleHasNoBands_isRejected() {
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.SLAB, 10);
        rule.setSlabs(List.of());

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BROKERAGE")
                .hasMessageContaining("band");
    }

    @Test
    void validate_whenSlabBandsOverlap_isRejected() {
        // Given — two bands claim 10000, so which applies would depend on declaration order
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.SLAB, 10);
        rule.setSlabs(List.of(slab(0.0, 20000.0, 50.0), slab(10000.0, null, 100.0)));

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void validate_whenSlabBandsLeaveAGap_isRejected() {
        // Given — a trade landing in the gap charges nothing, and looks exactly like a trade that
        // genuinely attracts no brokerage
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.SLAB, 10);
        rule.setSlabs(List.of(slab(0.0, 10000.0, 50.0), slab(20000.0, null, 100.0)));

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("gap");
    }

    @Test
    void validate_whenSlabBandsAreContiguous_passes() {
        ChargeRule rule = rule("BROKERAGE", ChargeBasis.SLAB, 10);
        rule.setSlabs(List.of(slab(0.0, 10000.0, 50.0), slab(10000.0, null, 100.0)));

        assertThatCode(() -> validator.validate(schedule(rule))).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- expressions

    @Test
    void validate_whenAFormulaCannotBeParsed_isRejected() {
        ChargeRule rule = rule("EXIT_LOAD", ChargeBasis.FORMULA, 10);
        rule.setFormula("#turnover *");

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("EXIT_LOAD");
    }

    @Test
    void validate_whenAnEligibilityPredicateCannotBeParsed_isRejected() {
        ChargeRule rule = brokerage();
        rule.setEligibility("#holdingDays <");

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BROKERAGE");
    }

    @Test
    void validate_whenAnExpressionNamesAVariableThatDoesNotExist_isRejected() {
        // Given — ADR-24. A typo parses cleanly, evaluates to null, and silently disables its rule
        // forever. Nothing else can catch this, because rate cards are data.
        ChargeRule rule = brokerage();
        rule.setEligibility("#equityOrientd == true");

        assertThatThrownBy(() -> validator.validate(schedule(rule)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("equityOrientd");
    }

    @Test
    void validate_whenAnExpressionNamesKnownVariables_passes() {
        // Given — the whole documented vocabulary in one card
        ChargeRule rule = rule("EXIT_LOAD", ChargeBasis.FORMULA, 20);
        rule.setFormula("#turnover * 0.01 + #quantity * #price + #lotSize + #charges['BROKERAGE']");
        rule.setEligibility("#holdingDays < 365 and #equityOriented == true and #side == 'SELL'"
                + " and #fundCategory == 'DEBT' and #planType == 'REGULAR' and #premium >= 0");

        assertThatCode(() -> validator.validate(schedule(brokerage(), rule))).doesNotThrowAnyException();
    }

    @Test
    void validate_reportsEveryProblemAtOnce() {
        // Given — fixing a seed file one exception at a time is a poor way to spend an afternoon
        ChargeRule missingRate = rule("STT", ChargeBasis.TURNOVER, 10);
        ChargeRule missingAmount = rule("DP", ChargeBasis.FLAT, 20);

        assertThatThrownBy(() -> validator.validate(schedule(missingRate, missingAmount)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STT")
                .hasMessageContaining("DP");
    }

    @Test
    void validate_whenAnyAmountIsNegative_isRejected() {
        // Given — a rate is not the only thing that can go below zero on a card
        ChargeRule negativeFlat = flat("BROKERAGE", -20.0, 10);
        ChargeRule negativePerUnit = rule("SEBI_FEE", ChargeBasis.PER_UNIT, 20);
        negativePerUnit.setPerUnitAmount(-0.05);
        ChargeRule negativeFloor = flat("DP", 13.5, 30);
        negativeFloor.setMinAmount(-1.0);
        ChargeRule negativeCap = flat("STT", 100.0, 40);
        negativeCap.setMaxAmount(-1.0);

        // When / Then
        assertThatThrownBy(() -> validator.validate(
                        schedule(negativeFlat, negativePerUnit, negativeFloor, negativeCap)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("negative flat amount")
                .hasMessageContaining("negative per unit amount")
                .hasMessageContaining("negative minimum amount")
                .hasMessageContaining("negative maximum amount");
    }

    @Test
    void validate_whenAnAmountIsZero_passes() {
        // Given — zero is a real rate, not a missing one. A discount broker's delivery brokerage
        // genuinely is nothing, and rejecting it would make the commonest card in India unpublishable.
        ChargeRule free = flat("BROKERAGE", 0.0, 10);

        assertThatCode(() -> validator.validate(schedule(free))).doesNotThrowAnyException();
    }

    @Test
    void validate_whenBandsAreDeclaredOutOfOrder_stillJudgesThemOnTheirBounds() {
        // Given — a card lists its highest band first. Bands are a set, not a sequence, so the
        // contiguity check has to sort before it compares rather than trusting the order authored.
        ChargeRule contiguous = rule("BROKERAGE", ChargeBasis.SLAB, 10);
        contiguous.setSlabs(List.of(slab(10000.0, null, 100.0), slab(0.0, 10000.0, 50.0)));

        ChargeRule gapped = rule("STT", ChargeBasis.SLAB, 20);
        gapped.setSlabs(List.of(slab(20000.0, null, 100.0), slab(0.0, 10000.0, 50.0)));

        // When / Then
        assertThatCode(() -> validator.validate(schedule(contiguous))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(schedule(gapped)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("gap");
    }

    // ---------------------------------------------------------------- fixtures

    private static ChargeScheduleEntity schedule(ChargeRule... rules) {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setScheduleCode("ZERODHA_EQ_DELIVERY_2025_04");
        schedule.setBrokerName(BrokerName.ZERODHA);
        schedule.setStartDate(LocalDate.of(2025, 4, 1));
        schedule.setStatus(EntityStatus.ACTIVE);
        schedule.setRules(new ArrayList<>(Arrays.asList(rules)));
        return schedule;
    }

    private static ChargeRule rule(String code, ChargeBasis basis, int order) {
        ChargeRule rule = new ChargeRule();
        rule.setCode(code);
        rule.setDisplayName(code);
        rule.setCategory(ChargeCategory.BROKERAGE);
        rule.setBasis(basis);
        rule.setSide(ChargeSide.BOTH);
        rule.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        rule.setAmountBasis(AmountBasis.TURNOVER);
        rule.setOrder(order);
        rule.setActive(true);
        return rule;
    }

    private static ChargeRule flat(String code, double amount, int order) {
        ChargeRule rule = rule(code, ChargeBasis.FLAT, order);
        rule.setFlatAmount(amount);
        return rule;
    }

    private static ChargeRule derived(String code, int order, List<String> baseCodes) {
        ChargeRule rule = rule(code, ChargeBasis.DERIVED, order);
        rule.setRate(18.0);
        rule.setBaseCodes(baseCodes);
        return rule;
    }

    private static ChargeRule brokerage() {
        return flat("BROKERAGE", 20.0, 10);
    }

    private static ChargeRule gst() {
        return derived("GST", 100, List.of("BROKERAGE"));
    }

    private static ChargeSlab slab(Double from, Double to, Double flatAmount) {
        return new ChargeSlab(from, to, null, flatAmount);
    }

    private static ChargeCatalogueEntity catalogued(String code) {
        ChargeCatalogueEntity entry = new ChargeCatalogueEntity();
        entry.setCode(code);
        entry.setStatus(EntityStatus.ACTIVE);
        return entry;
    }
}
