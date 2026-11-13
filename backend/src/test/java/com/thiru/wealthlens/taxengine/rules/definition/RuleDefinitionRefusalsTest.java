package com.thiru.wealthlens.taxengine.rules.definition;

import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What the rule definitions refuse, one bad field per case — the pack's rows are read into these records, so each case
 * is a malformed row refused at load. Each message is the record's own (W-240d, D-511).
 */
class RuleDefinitionRefusalsTest {

    private static final Money LIMIT = Money.of("200000");
    private static final Money NEGATIVE = Money.of("-1");
    private static final Rate SHARE = Rate.percent("10");
    private static final LocalDate DAY = LocalDate.of(2026, 4, 1);
    private static final MonthDay DUE = MonthDay.of(6, 15);
    private static final CapitalGainsPart LISTED = new CapitalGainsPart("LONG_TERM_LISTED_EQUITY");

    static Stream<Arguments> refusals() {
        return Stream.of(loans(), schedules(), shares(), periods(), orders(), singles()).flatMap(cases -> cases);
    }

    private static Stream<Arguments> loans() {
        String sanctioned = "A sanctioned-loan limit needs its limit, window and ceilings";
        String residence = "An own-residence interest limit needs its three limits";
        Optional<Money> none = Optional.empty();
        return Stream.of(
                refusal("SanctionedLoanLimit limit null", () -> new SanctionedLoanLimit(null, DAY, DAY, none, none, none,
                        false), sanctioned),
                refusal("SanctionedLoanLimit from null", () -> new SanctionedLoanLimit(LIMIT, null, DAY, none, none, none,
                        false), sanctioned),
                refusal("SanctionedLoanLimit to null", () -> new SanctionedLoanLimit(LIMIT, DAY, null, none, none, none,
                        false), sanctioned),
                refusal("SanctionedLoanLimit amount ceiling null", () -> new SanctionedLoanLimit(LIMIT, DAY, DAY, null,
                        none, none, false), sanctioned),
                refusal("SanctionedLoanLimit property ceiling null", () -> new SanctionedLoanLimit(LIMIT, DAY, DAY, none,
                        null, none, false), sanctioned),
                refusal("SanctionedLoanLimit stamp-duty ceiling null", () -> new SanctionedLoanLimit(LIMIT, DAY, DAY,
                        none, none, null, false), sanctioned),
                refusal("SanctionedLoanLimit window backwards", () -> new SanctionedLoanLimit(LIMIT, DAY, DAY.minusDays(1),
                        none, none, none, false), "A sanction window ends 2026-03-31, before it starts"),
                refusal("OwnResidenceInterestLimit conditionsMet null", () -> new OwnResidenceInterestLimit(null, LIMIT,
                        LIMIT, 5, Optional.empty()), residence),
                refusal("OwnResidenceInterestLimit anyOtherCase null", () -> new OwnResidenceInterestLimit(LIMIT, null,
                        LIMIT, 5, Optional.empty()), residence),
                refusal("OwnResidenceInterestLimit aggregate null", () -> new OwnResidenceInterestLimit(LIMIT, LIMIT,
                        null, 5, Optional.empty()), residence),
                refusal("OwnResidenceInterestLimit borrowedOnOrAfter null", () -> new OwnResidenceInterestLimit(LIMIT,
                        LIMIT, LIMIT, 5, null), residence),
                refusal("OwnResidenceInterestLimit years negative", () -> new OwnResidenceInterestLimit(LIMIT, LIMIT,
                        LIMIT, -1, Optional.empty()), "The years to complete a house may not be negative"));
    }

    private static Stream<Arguments> schedules() {
        String instalment = "An instalment needs a date and a share above nil";
        String step = "A deferment date needs its day, share, interest and lower share";
        String relief = "Deferment interest needs its relieved incomes";
        return Stream.of(
                refusal("InstalmentSchedule null", () -> new InstalmentSchedule(null),
                        "An instalment schedule needs at least one instalment"),
                refusal("InstalmentSchedule empty", () -> new InstalmentSchedule(List.of()),
                        "An instalment schedule needs at least one instalment"),
                refusal("InstalmentSchedule short of the whole", () -> new InstalmentSchedule(List.of(
                        new InstalmentSchedule.Instalment(DUE, Rate.percent("45")))),
                        "The last instalment must be the whole of the advance tax"),
                refusal("Instalment day null", () -> new InstalmentSchedule.Instalment(null, SHARE), instalment),
                refusal("Instalment share null", () -> new InstalmentSchedule.Instalment(DUE, null), instalment),
                refusal("Instalment share nil", () -> new InstalmentSchedule.Instalment(DUE, Rate.ZERO), instalment),
                refusal("DefermentInterest null", () -> new DefermentInterest(null, List.of(), DUE),
                        "Deferment interest needs at least one date"),
                refusal("DefermentInterest empty", () -> new DefermentInterest(List.of(), List.of(), DUE),
                        "Deferment interest needs at least one date"),
                refusal("DefermentInterest relieved incomes null", () -> new DefermentInterest(List.of(
                        new DefermentInterest.Step(DUE, SHARE, SHARE, Optional.empty())), null, DUE), relief),
                refusal("DefermentInterest paid-by day null", () -> new DefermentInterest(List.of(
                        new DefermentInterest.Step(DUE, SHARE, SHARE, Optional.empty())), List.of(), null), relief),
                refusal("Step day null", () -> new DefermentInterest.Step(null, SHARE, SHARE, Optional.empty()), step),
                refusal("Step share null", () -> new DefermentInterest.Step(DUE, null, SHARE, Optional.empty()), step),
                refusal("Step interest null", () -> new DefermentInterest.Step(DUE, SHARE, null, Optional.empty()), step),
                refusal("Step lower share null", () -> new DefermentInterest.Step(DUE, SHARE, SHARE, null), step));
    }

    private static Stream<Arguments> shares() {
        PolicyIssueWindow always = new PolicyIssueWindow(Optional.empty(), Optional.empty(), false);
        String limits = "Policy premium limits need both issue dates";
        String limit = "A policy premium limit must be above nil";
        String gift = "A gift threshold needs an amount above nil";
        GiftAggregation year = GiftAggregation.YEAR_TOTAL;
        return Stream.of(
                refusal("Band window null", () -> new PremiumShareBands.Band(null, SHARE),
                        "A premium share band needs its window and its share"),
                refusal("Band share null", () -> new PremiumShareBands.Band(always, null),
                        "A premium share band needs its window and its share"),
                refusal("PremiumShareBands null", () -> new PremiumShareBands(null),
                        "Premium share bands need at least one band"),
                refusal("PremiumShareBands empty", () -> new PremiumShareBands(List.of()),
                        "Premium share bands need at least one band"),
                refusal("PolicyIssueWindow from null", () -> new PolicyIssueWindow(null, Optional.empty(), false),
                        "A policy issue window's bounds may not be null"),
                refusal("PolicyIssueWindow to null", () -> new PolicyIssueWindow(Optional.empty(), null, false),
                        "A policy issue window's bounds may not be null"),
                refusal("PolicyIssueWindow backwards", () -> new PolicyIssueWindow(Optional.of(DAY),
                        Optional.of(DAY.minusDays(1)), false), "A policy issue window ends 2026-03-31, before it starts"),
                refusal("PolicyPremiumLimits unit-linked date null", () -> new PolicyPremiumLimits(null, LIMIT, DAY, LIMIT,
                        Optional.empty()), limits),
                refusal("PolicyPremiumLimits other date null", () -> new PolicyPremiumLimits(DAY, LIMIT, null, LIMIT,
                        Optional.empty()), limits),
                refusal("PolicyPremiumLimits IFSC date null", () -> new PolicyPremiumLimits(DAY, LIMIT, DAY, LIMIT, null),
                        limits),
                refusal("PolicyPremiumLimits a limit null", () -> new PolicyPremiumLimits(DAY, null, DAY, LIMIT,
                        Optional.empty()), limit),
                refusal("PolicyPremiumLimits a limit nil", () -> new PolicyPremiumLimits(DAY, LIMIT, DAY, Money.ZERO,
                        Optional.empty()), limit),
                refusal("GiftThreshold amount null", () -> new GiftThreshold(null, Optional.empty(), year), gift),
                refusal("GiftThreshold amount nil", () -> new GiftThreshold(Money.ZERO, Optional.empty(), year), gift),
                refusal("GiftThreshold share null", () -> new GiftThreshold(LIMIT, null, year), gift),
                refusal("GiftThreshold share nil", () -> new GiftThreshold(LIMIT, Optional.of(Rate.ZERO), year), gift),
                refusal("GiftThreshold aggregation null", () -> new GiftThreshold(LIMIT, Optional.empty(), null), gift),
                refusal("FamilyPensionShare limit null", () -> new FamilyPensionShare(3, null),
                        "A family pension share needs a limit above nil"),
                refusal("FamilyPensionShare limit nil", () -> new FamilyPensionShare(3, Money.ZERO),
                        "A family pension share needs a limit above nil"));
    }

    private static Stream<Arguments> periods() {
        String fee = "A late-return fee needs an income and two fees above nil";
        String months = "A reinvestment row's periods must each be positive months";
        String shortfall = "Advance-tax shortfall interest needs a share and a rate above nil";
        String indices = "Every index must be a positive number";
        Map<TaxYearId, Integer> missingIndex = new HashMap<>();
        missingIndex.put(TaxYearId.of(2026), null);
        return Stream.of(
                refusal("LateReturnFee income null", () -> new LateReturnFee(null, LIMIT, true, LIMIT), fee),
                refusal("LateReturnFee lower fee null", () -> new LateReturnFee(LIMIT, null, true, LIMIT), fee),
                refusal("LateReturnFee fee null", () -> new LateReturnFee(LIMIT, LIMIT, true, null), fee),
                refusal("LateReturnFee lower fee nil", () -> new LateReturnFee(LIMIT, Money.ZERO, true, LIMIT), fee),
                refusal("LateReturnFee fee nil", () -> new LateReturnFee(LIMIT, LIMIT, true, Money.ZERO), fee),
                refusal("ExemptReinvestment months before nil", () -> new ExemptReinvestment(0, 24, 36, LIMIT), months),
                refusal("ExemptReinvestment months after nil", () -> new ExemptReinvestment(12, 0, 36, LIMIT), months),
                refusal("ExemptReinvestment months after construction nil", () -> new ExemptReinvestment(12, 24, 0,
                        LIMIT), months),
                refusal("ExemptReinvestment cap null", () -> new ExemptReinvestment(12, 24, 36, null),
                        "A reinvestment row's cost cap may not be negative"),
                refusal("ExemptReinvestment cap negative", () -> new ExemptReinvestment(12, 24, 36, NEGATIVE),
                        "A reinvestment row's cost cap may not be negative"),
                refusal("AdvanceShortfallInterest share null", () -> new AdvanceShortfallInterest(null, SHARE), shortfall),
                refusal("AdvanceShortfallInterest rate null", () -> new AdvanceShortfallInterest(SHARE, null), shortfall),
                refusal("AdvanceShortfallInterest share nil", () -> new AdvanceShortfallInterest(Rate.ZERO, SHARE),
                        shortfall),
                refusal("AdvanceShortfallInterest rate nil", () -> new AdvanceShortfallInterest(SHARE, Rate.ZERO),
                        shortfall),
                refusal("IndexByYear null", () -> new IndexByYear(null), "An index table needs at least one year"),
                refusal("IndexByYear empty", () -> new IndexByYear(Map.of()), "An index table needs at least one year"),
                refusal("IndexByYear an index missing", () -> new IndexByYear(missingIndex), indices),
                refusal("IndexByYear an index nil", () -> new IndexByYear(Map.of(TaxYearId.of(2026), 0)), indices),
                refusal("TaxYearSpan negative", () -> new TaxYearSpan(-1), "A span of tax years may not be negative"),
                refusal("DayWindow negative", () -> new DayWindow(-1), "A window of days may not be negative"),
                refusal("AdvanceTaxLiability age nil", () -> new AdvanceTaxLiability(0, LIMIT),
                        "An advance-tax liability needs an age above nil and an amount above nil"),
                refusal("AdvanceTaxLiability amount null", () -> new AdvanceTaxLiability(60, null),
                        "An advance-tax liability needs an age above nil and an amount above nil"),
                refusal("AdvanceTaxLiability amount nil", () -> new AdvanceTaxLiability(60, Money.ZERO),
                        "An advance-tax liability needs an age above nil and an amount above nil"),
                refusal("InterestCalculation base null", () -> new InterestCalculation(null, true),
                        "Interest needs a whole number of rupees above nil"),
                refusal("InterestCalculation base nil", () -> new InterestCalculation(Money.ZERO, true),
                        "Interest needs a whole number of rupees above nil"),
                refusal("InterestCalculation base in paise", () -> new InterestCalculation(Money.of("100.50"), true),
                        "Interest needs a whole number of rupees above nil"),
                refusal("MonthlyInterest rate null", () -> new MonthlyInterest(null, true),
                        "Monthly interest needs a rate above nil"),
                refusal("MonthlyInterest rate nil", () -> new MonthlyInterest(Rate.ZERO, true),
                        "Monthly interest needs a rate above nil"));
    }

    private static Stream<Arguments> orders() {
        String declared = "A declared capital-gains part needs its part and its term";
        String transport = "A transport limit needs its conditions and monthly amount";
        Set<TransportDisability> blind = Set.of(TransportDisability.BLIND);
        return Stream.of(
                refusal("Declared part null", () -> new CapitalGainsParts.Declared(null, "LONG_TERM", true), declared),
                refusal("Declared term null", () -> new CapitalGainsParts.Declared(LISTED, null, true), declared),
                refusal("Declared term blank", () -> new CapitalGainsParts.Declared(LISTED, " ", true), declared),
                refusal("CapitalGainsParts null", () -> new CapitalGainsParts(null),
                        "The capital-gains parts need at least one part"),
                refusal("CapitalGainsParts empty", () -> new CapitalGainsParts(List.of()),
                        "The capital-gains parts need at least one part"),
                refusal("AbsorptionOrder null", () -> new AbsorptionOrder(null), "An absorption order needs at least one head"),
                refusal("AbsorptionOrder empty", () -> new AbsorptionOrder(List.of()),
                        "An absorption order needs at least one head"),
                refusal("AbsorptionOrder a head twice", () -> new AbsorptionOrder(List.of(HeadCode.SALARIES,
                        HeadCode.SALARIES)), "An absorption order names each head once"),
                refusal("SpecialRateShortfallOrder null", () -> new SpecialRateShortfallOrder(null),
                        "A special-rate shortfall order needs its parts"),
                refusal("SpecialRateShortfallOrder a part twice", () -> new SpecialRateShortfallOrder(List.of(LISTED,
                        LISTED)), "A special-rate shortfall order names each special-rate part once"),
                refusal("CapitalGainsAbsorptionOrder null", () -> new CapitalGainsAbsorptionOrder(null),
                        "A capital-gains absorption order needs its parts"),
                refusal("CapitalGainsAbsorptionOrder a part twice", () -> new CapitalGainsAbsorptionOrder(List.of(
                        LISTED, LISTED)), "A capital-gains absorption order names every part once"),
                refusal("ExcludedAssets null", () -> new ExcludedAssets(null),
                        "An exclusion lists at least one named kind, never OTHER"),
                refusal("ExcludedAssets empty", () -> new ExcludedAssets(Set.of()),
                        "An exclusion lists at least one named kind, never OTHER"),
                refusal("ExcludedAssets OTHER", () -> new ExcludedAssets(Set.of(MovableAssetKind.OTHER)),
                        "An exclusion lists at least one named kind, never OTHER"),
                refusal("DisabledTransportLimit conditions null", () -> new DisabledTransportLimit(null, LIMIT,
                        Optional.empty(), false), transport),
                refusal("DisabledTransportLimit conditions empty", () -> new DisabledTransportLimit(Set.of(), LIMIT,
                        Optional.empty(), false), transport),
                refusal("DisabledTransportLimit amount null", () -> new DisabledTransportLimit(blind, null,
                        Optional.empty(), false), transport),
                refusal("DisabledTransportLimit other-city amount null", () -> new DisabledTransportLimit(blind, LIMIT,
                        null, false), transport));
    }

    private static Stream<Arguments> singles() {
        String notModelled = "A provision not modelled needs what it is about and its effect";
        NotModelledProvision.TaxEffect raises = NotModelledProvision.TaxEffect.MAY_RAISE;
        return Stream.of(
                refusal("NotModelledProvision subject null", () -> new NotModelledProvision(null, raises), notModelled),
                refusal("NotModelledProvision subject blank", () -> new NotModelledProvision(" ", raises), notModelled),
                refusal("NotModelledProvision effect null", () -> new NotModelledProvision("s.10(14)", null), notModelled),
                refusal("WearAndTear rate null", () -> new WearAndTear(null, WearAndTearMethod.REDUCING_BALANCE),
                        "Wear and tear needs its rate and its method"),
                refusal("WearAndTear method null", () -> new WearAndTear(SHARE, null),
                        "Wear and tear needs its rate and its method"),
                refusal("SurvivingChildrenLimit count negative", () -> new SurvivingChildrenLimit(-1, DAY),
                        "A surviving-children limit needs a count of none or more"),
                refusal("SurvivingChildrenLimit date null", () -> new SurvivingChildrenLimit(2, null),
                        "A surviving-children limit needs a count of none or more"),
                refusal("SttChargeability null", () -> new SttChargeability(null), "An STT-chargeability row must name its code"),
                refusal("SttChargeability blank", () -> new SttChargeability(" "), "An STT-chargeability row must name its code"),
                refusal("LossCarryForward head null", () -> new LossCarryForward(null, new TaxYearSpan(8), true),
                        "A loss carried forward needs the head it is set off against"),
                refusal("LossCarryForward span null", () -> new LossCarryForward(HeadCode.HOUSE_PROPERTY, null, true),
                        "A loss carried forward needs the head it is set off against"),
                refusal("CashPaymentLimit null", () -> new CashPaymentLimit(null), "A cash payment limit must be nil or more"),
                refusal("CashPaymentLimit negative", () -> new CashPaymentLimit(NEGATIVE),
                        "A cash payment limit must be nil or more"),
                refusal("CapitalGainsPart null", () -> new CapitalGainsPart(null),
                        "CapitalGainsPart must be upper-case with underscores, 2-64 characters, e.g. LONG_TERM_LISTED_EQUITY,"
                                + " but was null"),
                refusal("CapitalGainsPart lower case", () -> new CapitalGainsPart("lower"), "but was \"lower\""),
                refusal("VehiclePath null", () -> new VehiclePath(null), "A vehicle-path row must name its computation"),
                refusal("SalaryBase null", () -> new SalaryBase(null),
                        "A salary base must say which test decides dearness allowance"),
                refusal("ParticularsThreshold null", () -> new ParticularsThreshold(null),
                        "A particulars threshold must state the amount it is exceeded above"),
                refusal("OwnResidenceHouses none", () -> new OwnResidenceHouses(0),
                        "At least one house may be an own residence"),
                refusal("IndexedCost null", () -> new IndexedCost(null),
                        "An indexed-cost row must name the year indexation starts from"),
                refusal("HeadTotal null", () -> new HeadTotal(null), "A head total needs its head"),
                refusal("DisallowanceBasis null", () -> new DisallowanceBasis(null),
                        "A disallowance basis must name the set it is the reason for"),
                refusal("ChargeDeduction null", () -> new ChargeDeduction(null), "A charge-deduction row must name its limb"),
                refusal("BucketPart null", () -> new BucketPart(null), "A bucket-part row must name its part"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }

    @Test
    void holds_whenTheYearIsBeforeOrPastATwoYearSpan_isFalseAndWithinItTrue() {
        // Given — a span that begins 2024-25 and runs two succeeding years, to 2026-27
        TaxYearSpan span = new TaxYearSpan(2);
        TaxYearId initial = TaxYearId.of(2024);

        // When / Then — TaxYearSpan.java:23
        assertThat(span.holds(initial, TaxYearId.of(2023))).isFalse();
        assertThat(span.holds(initial, TaxYearId.of(2024))).isTrue();
        assertThat(span.holds(initial, TaxYearId.of(2026))).isTrue();
        assertThat(span.holds(initial, TaxYearId.of(2027))).isFalse();
    }

    @Test
    void bandHolding_whenTwoBandsHoldOnePolicy_isRefused() {
        // Given — two unbounded bands, each holding every policy
        PolicyIssueWindow always = new PolicyIssueWindow(Optional.empty(), Optional.empty(), false);
        PremiumShareBands bands = new PremiumShareBands(List.of(new PremiumShareBands.Band(always, SHARE),
                new PremiumShareBands.Band(always, Rate.percent("20"))));

        // When / Then — PremiumShareBands.java:60
        assertThatThrownBy(() -> bands.bandHolding(DAY, false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 premium share bands hold a policy issued on 2026-04-01");
    }

    @Test
    void require_whenTheRuleDefinesAnotherKind_isAWrongRowNotAnInput() {
        // Given
        RuleId rule = new RuleId("RATE-SLAB-NEW-TAX-REGIME");
        Optional<RuleDefinition> monthly = Optional.of(new MonthlyInterest(Rate.percent("1"), true));

        // When / Then — RuleDefinitions.java:25
        assertThatThrownBy(() -> RuleDefinitions.require(monthly, DayWindow.class, rule))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("defines a MonthlyInterest, not a DayWindow");
    }
}
