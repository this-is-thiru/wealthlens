package com.thiru.wealthlens.taxengine.golden;

import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.HousingLoan;
import com.thiru.wealthlens.taxengine.input.LoanUse;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * One {@code DR 08} T6 case: the houses and the items of income from other sources it states, the person's age, and
 * what it expects — copied, never computed (D-211). Run through the shipped pipeline to Chapter VIII, since the heads
 * meet there.
 *
 * <p>An expected figure is one amount for every year the case runs in, or an amount per year where the case states
 * the years apart (T6-6).
 *
 * @param expectedHousePropertyIncome compared with {@code houseproperty.income}, by year; empty where unstated
 * @param expectedOtherSourcesIncome compared with {@code othersources.income}, by year; empty where unstated
 * @param expectedChapterEightDeduction compared with {@code chapterviii}, by year; empty where unstated
 * @param expectedNilRules rules the case says are disallowed: each has a line, and every line is nil
 * @param expectedAbsentRules rules the case says do not apply: no line cites them
 * @param expectedHousePropertyLossSetOff the house-property loss set off against the other heads, by year — the
 *     {@code SETOFF-HOUSE-PROPERTY} SET_OFF lines added up (D-217)
 * @param expectedHousePropertyLossDestroyed the house-property loss the new regime deems given full effect to, by year
 *     — the {@code SETOFF-HOUSE-PROPERTY} MEASURE line (S2 s.202(3); D-217)
 */
record HouseAndOtherSourcesGoldenCase(
        String id,
        String source,
        RegimeCode regime,
        Optional<Integer> ageAtYearEnd,
        List<TaxYearId> taxYears,
        List<Property> properties,
        List<OtherSourceItem> otherSources,
        Map<TaxYearId, Money> expectedHousePropertyIncome,
        Map<TaxYearId, Money> expectedOtherSourcesIncome,
        Map<TaxYearId, Money> expectedChapterEightDeduction,
        List<RuleId> expectedNilRules,
        List<RuleId> expectedAbsentRules,
        Map<TaxYearId, Money> expectedHousePropertyLossSetOff,
        Map<TaxYearId, Money> expectedHousePropertyLossDestroyed) {

    /**
     * A house as the case states it, owned alone, with one loan borrowed to acquire or construct it.
     *
     * <p>The case states which pre-construction instalment the year takes ("instalment 3 of 5"), not dates. The
     * house is dated into the tax year {@code instalment − 1} before the year run, which gives that instalment, and
     * the capital borrowed a year before that — or {@code completedYearsAfterBorrowingYear} more, where the case says
     * the house was completed that many years after the end of the tax year of borrowing (T6-5); the higher
     * own-residence limit's lender certificate is held.
     */
    record Property(HouseOccupancy occupancy, Money expectedLettingValue, Money rentReceivable, Money localTaxesPaid,
            Money interestPayable, Money preConstructionInterest, int preConstructionInstalment,
            int completedYearsAfterBorrowingYear) {

        HouseProperty in(TaxYearId year) {
            LocalDate acquired = year.startsOn().minusYears(preConstructionInstalment - 1L);
            HousingLoan loan = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION,
                    acquired.minusYears(1L + completedYearsAfterBorrowingYear),
                    interestPayable, preConstructionInterest, Money.ZERO, true, false);
            return new HouseProperty(occupancy, expectedLettingValue, rentReceivable, Money.ZERO, false,
                    localTaxesPaid, Rate.percent("100"), Money.ZERO, Optional.of(acquired), List.of(loan));
        }
    }

    List<HouseProperty> housePropertiesIn(TaxYearId year) {
        return properties.stream().map(property -> property.in(year)).toList();
    }

    static List<HouseAndOtherSourcesGoldenCase> loadAll(String directory) {
        return GoldenFiles.loadAll(directory, HouseAndOtherSourcesGoldenCase::of);
    }

    private static HouseAndOtherSourcesGoldenCase of(JsonNode node) {
        List<TaxYearId> years = new ArrayList<>();
        node.get("taxYears").forEach(year -> years.add(GoldenCase.taxYear(year.asString())));
        List<Property> properties = new ArrayList<>();
        node.path("houseProperties").forEach(house -> properties.add(new Property(
                HouseOccupancy.valueOf(house.get("occupancy").asString()),
                Money.of(house.get("expectedLettingValue").asString()),
                Money.of(house.get("rentReceivable").asString()),
                Money.of(house.get("localTaxesPaid").asString()),
                Money.of(house.get("interestPayable").asString()),
                Money.of(house.get("preConstructionInterest").asString()),
                house.get("preConstructionInstalment").asInt(),
                house.path("completedYearsAfterBorrowingYear").asInt(0))));
        List<OtherSourceItem> items = new ArrayList<>();
        node.path("otherSources").forEach(item -> items.add(new OtherSourceItem(
                OtherSourceKind.valueOf(item.get("kind").asString()), Money.of(item.get("received").asString()),
                Money.ZERO, Money.of(item.path("interestExpense").asString("0")), Money.ZERO, false)));
        JsonNode expected = node.get("expected");
        return new HouseAndOtherSourcesGoldenCase(
                node.get("id").asString(),
                node.get("source").asString(),
                new RegimeCode(node.get("regime").asString()),
                node.has("ageAtYearEnd") ? Optional.of(node.get("ageAtYearEnd").asInt()) : Optional.empty(),
                List.copyOf(years),
                List.copyOf(properties),
                List.copyOf(items),
                byYear(expected, "housePropertyIncome", years),
                byYear(expected, "otherSourcesIncome", years),
                byYear(expected, "chapterEightDeduction", years),
                rules(expected, "nilRules"),
                rules(expected, "absentRules"),
                byYear(expected, "housePropertyLossSetOff", years),
                byYear(expected, "housePropertyLossDestroyed", years));
    }

    /** One amount for every year, or an object of amounts keyed by year label. */
    private static Map<TaxYearId, Money> byYear(JsonNode expected, String field, List<TaxYearId> years) {
        Map<TaxYearId, Money> amounts = new HashMap<>();
        JsonNode value = expected.path(field);
        if (value.isString()) {
            years.forEach(year -> amounts.put(year, Money.of(value.asString())));
        } else if (value.isObject()) {
            value.propertyNames().forEach(label -> amounts.put(GoldenCase.taxYear(label),
                    Money.of(value.get(label).asString())));
        }
        return Map.copyOf(amounts);
    }

    private static List<RuleId> rules(JsonNode expected, String field) {
        List<RuleId> rules = new ArrayList<>();
        expected.path(field).forEach(rule -> rules.add(new RuleId(rule.asString())));
        return List.copyOf(rules);
    }

    @Override
    public String toString() {
        return id;
    }
}
