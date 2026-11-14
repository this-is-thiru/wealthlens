package com.thiru.wealthlens.taxengine.rules.filing;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.condition.AgeSubject;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.Declarations;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
import com.thiru.wealthlens.taxengine.rules.condition.ReturnFacts;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * TY 2025-26's shipped ITR-1 and ITR-2 rows against the Income-tax Rules, 1962 r.12, kept live by the Income-tax Rules,
 * 2026 r.164(14), as in force for AY 2026-27 (W-090d, D-652). r.12 is rebuilt from its Gazette amendment chain
 * (sources/2026-11-13-itr-forms-ty-2025-26.md); each case cites the item and the amendment that wrote it. Each person
 * is built by hand from a resident salaried individual of 45 with ₹12,00,000 and one house property, then changed in
 * one fact, so each case moves one clause across its line.
 */
class ItrForms2025Test {

    private static final TaxYearId TY_2025_26 = TaxYearId.of(2025);
    private static final RulePack PACK = ShippedRulePacks.compile("rulepacks", Set.of(TY_2025_26)).load(TY_2025_26);
    private static final ItrFormSpec ITR_1 = PACK.itrForms().get(new ItrFormCode("ITR-1"));
    private static final ItrFormSpec ITR_2 = PACK.itrForms().get(new ItrFormCode("ITR-2"));
    private static final CapitalGainsPart LONG_LISTED = new CapitalGainsPart("LONG_TERM_LISTED_EQUITY");
    private static final CapitalGainsPart SHORT_SLAB = new CapitalGainsPart("SHORT_TERM_SLAB");
    private static final DeclarationCode AGRICULTURAL = new DeclarationCode("AGRICULTURAL_INCOME");

    /** The thirteen yes-or-no declarations r.12(1)(a)'s proviso bars on: (I) – (IC), (IE) – (IG), (II) (two), (V) – (VIII). */
    private static final List<String> YES_NO_BARS = List.of("ASSETS_OUTSIDE_INDIA", "SIGNING_AUTHORITY_OUTSIDE_INDIA",
            "INCOME_FROM_OUTSIDE_INDIA", "INCOME_APPORTIONED_BETWEEN_SPOUSES", "DIRECTOR_IN_COMPANY",
            "UNLISTED_EQUITY_HELD", "TDS_ASSESSABLE_IN_ANOTHERS_HANDS", "DOUBLE_TAXATION_AGREEMENT_RELIEF",
            "UNILATERAL_FOREIGN_TAX_DEDUCTION", "INCOME_TAXABLE_UNDER_S115BBDA", "UNEXPLAINED_INCOME_S115BBE",
            "CASH_WITHDRAWAL_TAX_DEDUCTED", "START_UP_SHARE_TAX_DEFERRED");

    /** One person and their computed return, changed one fact at a time. */
    private static final class Person {
        private ResidentialStatus residence = ResidentialStatus.RESIDENT;
        private int age = 45;
        private Money totalIncome = Money.of("1200000");
        private final Set<HeadCode> headsWithIncome = EnumSet.of(HeadCode.SALARIES);
        private int housesOwned = 1;
        private final Set<String> answeredYes = new HashSet<>();
        private Money agricultural = Money.ZERO;
        private final Map<HeadCode, Money> incomeByHead = everyHead("0");
        private final Map<CapitalGainsPart, Money> gains = new HashMap<>();
        private final Set<RuleId> deductions = new HashSet<>();
        private final Map<HeadCode, Money> broughtForward = everyHead("0");
        private final Map<HeadCode, Money> toCarryForward = everyHead("0");

        private static Map<HeadCode, Money> everyHead(String amount) {
            Map<HeadCode, Money> heads = new EnumMap<>(HeadCode.class);
            for (HeadCode head : List.of(HeadCode.SALARIES, HeadCode.HOUSE_PROPERTY, HeadCode.OTHER_SOURCES,
                    HeadCode.CAPITAL_GAINS)) {
                heads.put(head, Money.of(amount));
            }
            return heads;
        }

        private ConditionContext context() {
            Map<DeclarationCode, Boolean> yesNo = new HashMap<>();
            YES_NO_BARS.forEach(code -> yesNo.put(new DeclarationCode(code), answeredYes.contains(code)));
            return new ConditionContext(residence, PersonType.INDIVIDUAL, Map.of(AgeSubject.SELF, age), Map.of(),
                    Map.of(NodeIds.ROUND_TOTALINCOME, totalIncome), headsWithIncome, Set.of(), Set.of(), ItemFacts.NONE,
                    new Declarations(yesNo, Map.of(AGRICULTURAL, agricultural)), Optional.of(housesOwned),
                    ReturnFacts.of(incomeByHead, gains, deductions, broughtForward, toCarryForward));
        }
    }

    private static boolean allHold(List<Condition> conditions, ConditionContext ctx) {
        return conditions.stream().allMatch(condition -> condition.holds(ctx));
    }

    /** Open to the person: every positive condition holds and no bar does. */
    private static boolean opensTo(ItrFormSpec form, Person person) {
        ConditionContext ctx = person.context();
        return allHold(form.positiveConditions(), ctx) && form.bars().stream().noneMatch(bar -> bar.condition().holds(ctx));
    }

    private static Set<FurnishingManner> mannersOpenTo(ItrFormSpec form, Person person) {
        ConditionContext ctx = person.context();
        Set<FurnishingManner> open = new HashSet<>();
        form.furnishing().stream().filter(option -> allHold(option.conditions(), ctx))
                .forEach(option -> open.add(option.manner()));
        return open;
    }

    @Test
    void forms_whenRead_serveTheKindsAndPersonsOfRule12() {
        // Then — C1a: s.139(1), s.142(1)(i), s.148; r.12(1)(a) an individual; (c) an individual or HUF, not under (a)
        Set<ReturnKind> kinds = Set.of(new ReturnKind("S139_1"), new ReturnKind("S142_1_I"), new ReturnKind("S148"));
        assertThat(ITR_1.returnKinds()).isEqualTo(kinds);
        assertThat(ITR_2.returnKinds()).isEqualTo(kinds);
        assertThat(ITR_1.appliesTo()).containsExactly(PersonType.INDIVIDUAL);
        assertThat(ITR_2.appliesTo()).containsExactlyInAnyOrder(PersonType.INDIVIDUAL, PersonType.HUF);
        assertThat(ITR_2.excludes()).containsExactly(ITR_1.code());
        assertThat(ITR_1.cascadeOrder()).isLessThan(ITR_2.cascadeOrder());
    }

    @Test
    void declarations_whenRead_askTheFourteenQuestionsRule12BarsOn() {
        // Then — the thirteen yes-or-no bars and (III)'s amount; r.164(3)(n)'s SPECIAL_CASE_INCOME is not this year's
        assertThat(PACK.declarations().keySet().stream().map(Object::toString).sorted().toList())
                .containsExactlyElementsOf(java.util.stream.Stream.concat(YES_NO_BARS.stream(),
                        java.util.stream.Stream.of("AGRICULTURAL_INCOME")).sorted().toList());
    }

    @Test
    void itr1_whenAResidentHasSalaryAlone_opens() {
        // Given
        Person salaried = new Person();

        // When / Then — and ITR-2's own condition holds too, which its exclusion of ITR-1 resolves
        assertThat(opensTo(ITR_1, salaried)).isTrue();
        assertThat(opensTo(ITR_2, salaried)).isTrue();
    }

    @Test
    void itr1_whenThePersonIsNotOrdinarilyResident_doesNotOpen() {
        // Given — r.12(1)(a) (2018): "an individual who is a resident other than not ordinarily resident"
        Person rnor = new Person();
        rnor.residence = ResidentialStatus.RNOR;

        // When / Then
        assertThat(opensTo(ITR_1, rnor)).isFalse();
        assertThat(opensTo(ITR_2, rnor)).isTrue();
    }

    @Test
    void itr1_whenSection112AGainsReachTheLimit_opensAndOneRupeeMoreDoesNot() {
        // Given — r.12(1)(a)(iv) (2025): "long-term capital gains under section 112A not exceeding ₹1,25,000"
        Person atLimit = new Person();
        atLimit.gains.put(LONG_LISTED, Money.of("125000"));
        Person above = new Person();
        above.gains.put(LONG_LISTED, Money.of("125001"));

        // When / Then
        assertThat(opensTo(ITR_1, atLimit)).isTrue();
        assertThat(opensTo(ITR_1, above)).isFalse();
    }

    @Test
    void itr1_whenThereIsAShortTermGainOfOneRupee_doesNotOpen() {
        // Given — r.12(1)(a)(iv) (2025): "only long-term capital gains under section 112A"
        Person shortTerm = new Person();
        shortTerm.gains.put(SHORT_SLAB, Money.of("1"));

        // When / Then
        assertThat(opensTo(ITR_1, shortTerm)).isFalse();
    }

    @Test
    void itr1_whenThePersonOwnsThreeHouses_doesNotOpen() {
        // Given — r.12(1)(a)(ii): "one house property" → "two house properties" (Gazette 271423 p.15)
        Person two = new Person();
        two.housesOwned = 2;
        Person three = new Person();
        three.housesOwned = 3;

        // When / Then
        assertThat(opensTo(ITR_1, two)).isTrue();
        assertThat(opensTo(ITR_1, three)).isFalse();
    }

    @Test
    void itr1_whenALossCrossesYearsUnderHousePropertyOrCapitalGains_doesNotOpen() {
        // Given — r.12(1)(a)(ii) (2018) and (iv) (2025): no loss brought forward or to be carried forward
        Person housePropertyBroughtForward = new Person();
        housePropertyBroughtForward.broughtForward.put(HeadCode.HOUSE_PROPERTY, Money.of("1"));
        Person housePropertyToCarry = new Person();
        housePropertyToCarry.toCarryForward.put(HeadCode.HOUSE_PROPERTY, Money.of("1"));
        Person capitalBroughtForward = new Person();
        capitalBroughtForward.broughtForward.put(HeadCode.CAPITAL_GAINS, Money.of("1"));
        Person capitalToCarry = new Person();
        capitalToCarry.toCarryForward.put(HeadCode.CAPITAL_GAINS, Money.of("1"));

        // When / Then
        assertThat(opensTo(ITR_1, housePropertyBroughtForward)).isFalse();
        assertThat(opensTo(ITR_1, housePropertyToCarry)).isFalse();
        assertThat(opensTo(ITR_1, capitalBroughtForward)).isFalse();
        assertThat(opensTo(ITR_1, capitalToCarry)).isFalse();
    }

    @Test
    void itr1_whenOtherSourcesShowALossOfTheYear_doesNotOpen() {
        // Given — r.12(1)(a)(iii) (2013): "does not have any loss under the head"
        Person loss = new Person();
        loss.incomeByHead.put(HeadCode.OTHER_SOURCES, Money.of("-1"));

        // When / Then
        assertThat(opensTo(ITR_1, loss)).isFalse();
    }

    @Test
    void itr1_whenTotalIncomeIsFiftyLakh_opensAndTheNextTenRupeesDoNot() {
        // Given — proviso (IV) (2017): "exceeding fifty lakh rupees", on total income rounded to ₹10 (s.288A)
        Person fiftyLakh = new Person();
        fiftyLakh.totalIncome = Money.of("5000000");
        Person above = new Person();
        above.totalIncome = Money.of("5000010");

        // When / Then
        assertThat(opensTo(ITR_1, fiftyLakh)).isTrue();
        assertThat(opensTo(ITR_1, above)).isFalse();
    }

    @Test
    void itr1_whenAgriculturalIncomeIsFiveThousand_opensAndOneRupeeMoreDoesNot() {
        // Given — proviso (III): "agricultural income, exceeding five thousand rupees"
        Person atLimit = new Person();
        atLimit.agricultural = Money.of("5000");
        Person above = new Person();
        above.agricultural = Money.of("5001");

        // When / Then
        assertThat(opensTo(ITR_1, atLimit)).isTrue();
        assertThat(opensTo(ITR_1, above)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ASSETS_OUTSIDE_INDIA", "SIGNING_AUTHORITY_OUTSIDE_INDIA", "INCOME_FROM_OUTSIDE_INDIA",
        "INCOME_APPORTIONED_BETWEEN_SPOUSES", "DIRECTOR_IN_COMPANY", "UNLISTED_EQUITY_HELD",
        "TDS_ASSESSABLE_IN_ANOTHERS_HANDS", "DOUBLE_TAXATION_AGREEMENT_RELIEF", "UNILATERAL_FOREIGN_TAX_DEDUCTION",
        "INCOME_TAXABLE_UNDER_S115BBDA", "UNEXPLAINED_INCOME_S115BBE", "CASH_WITHDRAWAL_TAX_DEDUCTED",
        "START_UP_SHARE_TAX_DEFERRED"})
    void itr1_whenADeclarationIsAnsweredYes_isBarred(String code) {
        // Given — proviso (I) – (IC), (IE) – (IG), (II), (V) – (VIII)
        Person declared = new Person();
        declared.answeredYes.add(code);

        // When / Then
        assertThat(opensTo(ITR_1, declared)).isFalse();
        assertThat(opensTo(ITR_2, declared)).isTrue();
    }

    @Test
    void itr1_whenASection57DeductionOtherThanFamilyPensionWasAllowed_isBarred() {
        // Given — proviso (ID) (2019): "deduction under section 57, other than deduction claimed under clause (iia)";
        // commuted pension is a s.10(10A) exemption in the 1961 Act, not a s.57 deduction
        Person commission = new Person();
        commission.deductions.add(new RuleId("OS-BANKER-COMMISSION"));
        Person dividendInterest = new Person();
        dividendInterest.deductions.add(new RuleId("OS-DIVIDEND-INTEREST"));
        Person familyPension = new Person();
        familyPension.deductions.add(new RuleId("OS-FAMILY-PENSION"));
        Person familyPensionNewRegime = new Person();
        familyPensionNewRegime.deductions.add(new RuleId("OS-FAMILY-PENSION-NEW-TAX-REGIME"));
        Person commutedPension = new Person();
        commutedPension.deductions.add(new RuleId("OS-COMMUTED-PENSION"));

        // When / Then
        assertThat(opensTo(ITR_1, commission)).isFalse();
        assertThat(opensTo(ITR_1, dividendInterest)).isFalse();
        assertThat(opensTo(ITR_1, familyPension)).isTrue();
        assertThat(opensTo(ITR_1, familyPensionNewRegime)).isTrue();
        assertThat(opensTo(ITR_1, commutedPension)).isTrue();
    }

    @Test
    void forms_whenThereIsBusinessIncome_neitherOpens() {
        // Given — r.12(1)(a)(i) – (iv) a closed list; (c) "does not include income under the head" business (2018)
        Person business = new Person();
        business.headsWithIncome.add(HeadCode.BUSINESS_OR_PROFESSION);

        // When / Then
        assertThat(opensTo(ITR_1, business)).isFalse();
        assertThat(opensTo(ITR_2, business)).isFalse();
    }

    @Test
    void furnishing_whenTheFilerIsEighty_opensPaperOnItr1Only() {
        // Given — r.12(3) Table Sl. 1 (b) (2019): "eighty years or more", SAHAJ (ITR-1) or SUGAM (ITR-4); (c) otherwise
        Person eighty = new Person();
        eighty.age = 80;
        Person seventyNine = new Person();
        seventyNine.age = 79;
        Set<FurnishingManner> electronic = Set.of(new FurnishingManner("DIGITAL_SIGNATURE"),
                new FurnishingManner("ELECTRONIC_VERIFICATION_CODE"), new FurnishingManner("ELECTRONIC_WITH_ITR_V"));
        FurnishingManner paper = new FurnishingManner("PAPER");

        // When / Then
        assertThat(mannersOpenTo(ITR_1, eighty)).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.concat(electronic.stream(), java.util.stream.Stream.of(paper)).toList());
        assertThat(mannersOpenTo(ITR_1, seventyNine)).isEqualTo(electronic);
        assertThat(mannersOpenTo(ITR_2, eighty)).isEqualTo(electronic);
    }
}
