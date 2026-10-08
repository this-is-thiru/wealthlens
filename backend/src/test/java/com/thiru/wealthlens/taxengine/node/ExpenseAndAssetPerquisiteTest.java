package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerAssetTransfer;
import com.thiru.wealthlens.taxengine.input.EmployerAssetUse;
import com.thiru.wealthlens.taxengine.input.EmployerExpenseBenefit;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.ExpenseBenefitKind;
import com.thiru.wealthlens.taxengine.input.OfficialUseRecord;
import com.thiru.wealthlens.taxengine.input.TransferredAssetKind;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.definition.MovableAssetKind;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Holidays, credit-card and club bills, other benefits, and the use and transfer of the employer's assets (S4 r.15(5)
 * Table IV Sl. 2, 5–9; Rules 1962 r.3(7)(ii), (v)–(ix); D-119). Each figure is worked by hand: 10% a year for use,
 * 50% and 20% reducing balance and 10% straight line for transfer; TY 2026-27 excludes tablets and mobile phones too,
 * and joins the club exclusions (OQ-12).
 */
class ExpenseAndAssetPerquisiteTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final OfficialUseRecord RECORDS = new OfficialUseRecord(true, Optional.empty());

    private static EmployerExpenseBenefit expense(ExpenseBenefitKind kind, String amount, String recovered) {
        return new EmployerExpenseBenefit(0, kind, Money.of(amount), Money.of(recovered), OfficialUseRecord.none(),
                false, false);
    }

    private static EmployerExpenseBenefit club(boolean business, OfficialUseRecord records, boolean uniform) {
        return new EmployerExpenseBenefit(0, ExpenseBenefitKind.CLUB, Money.of("30000"), Money.ZERO, records, business,
                uniform);
    }

    private static EmployerAssetUse owned(MovableAssetKind kind, String cost, int months) {
        return new EmployerAssetUse(0, kind, Optional.of(Money.of(cost)), Optional.empty(), months, Money.ZERO);
    }

    private static EmployerAssetTransfer transfer(TransferredAssetKind kind, String cost, int years, String price) {
        return new EmployerAssetTransfer(0, kind, Money.of(cost), years, Money.of(price));
    }

    private static NodeResult run(int year, List<EmployerExpenseBenefit> expenses, List<EmployerAssetUse> lent,
            List<EmployerAssetTransfer> transferred) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, NEW_TAX_REGIME,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(List.of(new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty())),
                        List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), expenses, lent, transferred, List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), List.of()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
        return NodeHarness.with(pack)
                .regime(NEW_TAX_REGIME)
                .inputs(inputs)
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_ACCOM, List.of(Money.ZERO))))
                .given(NodeIds.SALARY_EXEMPTIONS, MoneyValue.of(Money.ZERO))
                .given(NodeIds.SALARY_MONETARYINCOME, MoneyValue.of(Money.ZERO))
                .run(new PerquisiteNode());
    }

    private static NodeResult expenses(int year, EmployerExpenseBenefit... entries) {
        return run(year, List.of(entries), List.of(), List.of());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAHolidayIsPaidFor_isTheExpenditureLessTheRecovery(int year) {
        // When
        NodeResult result = expenses(year, expense(ExpenseBenefitKind.HOLIDAY, "60000", "10000"));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("50000")));
        assertThat(result.trace().getFirst().provenance().verificationLevel()).isEqualTo(year == 2025
                ? VerificationLevel.UNVERIFIED : VerificationLevel.SOURCE_CHECKED);
    }

    @Test
    void evaluate_whenACreditCardIsWhollyOfficialWithRecords_isNilAndOtherwiseInFull() {
        // When
        NodeResult official = expenses(2026, new EmployerExpenseBenefit(0, ExpenseBenefitKind.CREDIT_CARD,
                Money.of("40000"), Money.ZERO, RECORDS, true, false));
        NodeResult personal = expenses(2026, expense(ExpenseBenefitKind.CREDIT_CARD, "40000", "0"));

        // Then
        assertThat(official.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(personal.value()).isEqualTo(MoneyValue.of(Money.of("40000")));
    }

    @Test
    void evaluate_whenAClubBillIsForBusinessWithRecordsInTaxYear2025_isNilAlone() {
        // When — r.3(7)(vi)(B), its own exclusion
        NodeResult result = expenses(2025, club(true, RECORDS, false));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.PERQ_CLUB_BUSINESS);
    }

    @Test
    void evaluate_whenHealthClubFacilitiesAreUniformInTaxYear2025_isNilAlone() {
        // When — r.3(7)(vi)(C), separately
        NodeResult result = expenses(2025, club(false, OfficialUseRecord.none(), true));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace().getFirst().rule()).isEqualTo(RuleIds.PERQ_CLUB_UNIFORM);
    }

    @Test
    void evaluate_whenAClubBillIsForBusinessWithRecordsButNotUniformInTaxYear2026_isSalary() {
        // When — Table IV Sl. 6 col. D (b) needs all three (D-119 C2a, OQ-12)
        NodeResult alone = expenses(2026, club(true, RECORDS, false));
        NodeResult together = expenses(2026, club(true, RECORDS, true));

        // Then
        assertThat(alone.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
        assertThat(together.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void evaluate_whenAnOtherBenefitOrATelephone_isItsCostOrNil() {
        // When
        NodeResult gym = expenses(2026, expense(ExpenseBenefitKind.OTHER, "24000", "0"));
        NodeResult phone = expenses(2026, expense(ExpenseBenefitKind.TELEPHONE, "18000", "0"));

        // Then
        assertThat(gym.value()).isEqualTo(MoneyValue.of(Money.of("24000")));
        assertThat(phone.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenAnAssetIsLentAllYearOrHalf_isTenPercentOfItsCostForTheMonths(int year) {
        // When — 10% × 1,00,000; for 6 months, 10% × 50,000
        NodeResult year12 = run(year, List.of(), List.of(owned(MovableAssetKind.OTHER, "100000", 12)), List.of());
        NodeResult year6 = run(year, List.of(), List.of(owned(MovableAssetKind.OTHER, "100000", 6)), List.of());

        // Then
        assertThat(year12.value()).isEqualTo(MoneyValue.of(Money.of("10000")));
        assertThat(year6.value()).isEqualTo(MoneyValue.of(Money.of("5000")));
    }

    @Test
    void evaluate_whenAMobilePhoneIsLent_isValuedInTaxYear2025AndExcludedIn2026() {
        // When
        NodeResult before = run(2025, List.of(), List.of(owned(MovableAssetKind.MOBILE_PHONE, "60000", 12)), List.of());
        NodeResult after = run(2026, List.of(), List.of(owned(MovableAssetKind.MOBILE_PHONE, "60000", 12)), List.of());

        // Then — 10% × 60,000 = 6,000; then nil
        assertThat(before.value()).isEqualTo(MoneyValue.of(Money.of("6000")));
        assertThat(after.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(after.trace().getFirst().rule()).isEqualTo(RuleIds.PERQ_ASSET_USE_EXCLUDED);
    }

    @Test
    void evaluate_whenAHiredAssetIsLent_isTheRentPaidLessTheRecovery() {
        // When
        NodeResult result = run(2026, List.of(), List.of(new EmployerAssetUse(0, MovableAssetKind.OTHER,
                Optional.empty(), Optional.of(Money.of("12000")), 12, Money.of("2000"))), List.of());

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("10000")));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void evaluate_whenACarIsTransferred_isItsReducingBalanceLessThePrice(int year) {
        // When — 10,00,000 × 0.8² = 6,40,000, less 5,00,000
        NodeResult result = run(year, List.of(), List.of(),
                List.of(transfer(TransferredAssetKind.MOTOR_CAR, "1000000", 2, "500000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("140000")));
    }

    @Test
    void evaluate_whenALaptopIsTransferred_isHalvedEachYear() {
        // When — 1,00,000 × 0.5 = 50,000, less 20,000
        NodeResult result = run(2026, List.of(), List.of(),
                List.of(transfer(TransferredAssetKind.COMPUTER_OR_ELECTRONIC, "100000", 1, "20000")));

        // Then
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("30000")));
    }

    @Test
    void evaluate_whenOtherAssetsAreTransferred_loseTenPercentOfCostEachYearDownToNil() {
        // When — 1,00,000 − 3 × 10,000 = 70,000; after 12 years nothing is left
        NodeResult three = run(2026, List.of(), List.of(),
                List.of(transfer(TransferredAssetKind.OTHER, "100000", 3, "0")));
        NodeResult twelve = run(2026, List.of(), List.of(),
                List.of(transfer(TransferredAssetKind.OTHER, "100000", 12, "0")));

        // Then
        assertThat(three.value()).isEqualTo(MoneyValue.of(Money.of("70000")));
        assertThat(twelve.value()).isEqualTo(MoneyValue.of(Money.ZERO));
    }

    @Test
    void construct_whenAHolidayClaimsOfficialRecords_isRefused() {
        // When / Then — only a card or club reads them
        assertThatThrownBy(() -> new EmployerExpenseBenefit(0, ExpenseBenefitKind.HOLIDAY, Money.of("1000"),
                        Money.ZERO, RECORDS, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credit card or club");
    }

    @Test
    void construct_whenAnAssetHasBothCostAndRent_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new EmployerAssetUse(0, MovableAssetKind.OTHER, Optional.of(Money.of("1000")),
                        Optional.of(Money.of("100")), 12, Money.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both");
    }
}
