package com.thiru.wealthlens.taxengine.golden;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.Journey;
import com.thiru.wealthlens.taxengine.input.JourneyMode;
import com.thiru.wealthlens.taxengine.input.LeaveTravelBlock;
import com.thiru.wealthlens.taxengine.input.LifeInsurancePolicy;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.RetirementFundHistory;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * One {@code DR 08} case stated as salary facts rather than a total income: the pay, whether the terms of
 * employment provide for dearness allowance, the homes occupied, the employer's retirement contributions and the
 * funds' history, the Chapter VIII payments and policies, the journeys on leave and the blocks' earlier claims, and
 * what the case expects — copied, never computed (D-104, D-128, D-153). Run through the shipped pipeline to income
 * under "Salaries", or on to Chapter VIII where the case expects a deduction.
 *
 * <p>A location is entered as the year's table names it, so a case whose home is listed in one year and not in the
 * other is two files, one per year.
 *
 * @param expectedExemption the case's "Expected" exemption, compared with {@code salary.exemptions}, where it states one
 * @param expectedLimitedBy the limb or reason the case names, as the pack words it, where the case names one
 * @param expectedEmployerContributionCharges the employer contributions the case adds to salary, compared with
 *     {@code employer.contrib.charges}, where it states them
 * @param expectedChapterEightDeduction the Chapter VIII deductions the case allows, compared with {@code chapterviii}
 * @param expectedScheduleXvAggregate the s.123 aggregate the case states before the ceiling — the trace's
 *     {@code DED-SCHEDULE-XV} line's input
 */
record SalaryGoldenCase(
        String id,
        String source,
        RegimeCode regime,
        List<TaxYearId> taxYears,
        Salary salary,
        Optional<Money> expectedExemption,
        Optional<String> expectedLimitedBy,
        Optional<Money> expectedEmployerContributionCharges,
        Optional<Money> expectedChapterEightDeduction,
        Optional<Money> expectedScheduleXvAggregate) {

    /**
     * What the case enters for the one employment, the homes occupied, the employer's retirement funds, the Chapter
     * VIII payments and policies, the journeys on leave and the earlier claims in each block.
     */
    record Salary(List<SalaryComponent> pay, boolean termsIncludeDearnessAllowance, List<Tenancy> tenancies,
            List<EmployerFundContribution> employerContributions,
            Optional<RetirementFundHistory> retirementFundHistory, List<QualifyingPayment> qualifyingPayments,
            List<LifeInsurancePolicy> lifeInsurancePolicies, List<Journey> leaveTravel,
            Map<Integer, LeaveTravelBlock> leaveTravelLedger) {
    }

    /** Whether the case is run on to Chapter VIII. */
    boolean expectsDeduction() {
        return expectedChapterEightDeduction.isPresent() || expectedScheduleXvAggregate.isPresent();
    }

    static List<SalaryGoldenCase> loadAll(String directory) {
        return GoldenFiles.loadAll(directory, SalaryGoldenCase::of);
    }

    private static SalaryGoldenCase of(JsonNode node) {
        List<TaxYearId> years = new ArrayList<>();
        node.get("taxYears").forEach(year -> years.add(GoldenCase.taxYear(year.asString())));
        JsonNode salary = node.get("salary");
        List<SalaryComponent> pay = new ArrayList<>();
        salary.get("pay").propertyNames().forEach(code -> pay.add(
                new SalaryComponent(new ComponentCode(code), Money.of(salary.get("pay").get(code).asString()))));
        List<Tenancy> tenancies = new ArrayList<>();
        List<EmployerFundContribution> contributions = new ArrayList<>();
        if (salary.has("employerContributions")) {
            salary.get("employerContributions").forEach(contribution -> contributions.add(new EmployerFundContribution(
                    0, EmployerFund.valueOf(contribution.get("fund").asString()),
                    Money.of(contribution.get("amount").asString()))));
        }
        salary.get("tenancies").forEach(home -> tenancies.add(new Tenancy(
                DateRange.between(LocalDate.parse(home.get("from").asString()),
                        LocalDate.parse(home.get("to").asString())),
                home.get("location").asString(), home.get("ownedByAssessee").asBoolean(),
                Money.of(home.get("rentPaid").asString()))));
        JsonNode expected = node.get("expected");
        return new SalaryGoldenCase(
                node.get("id").asString(),
                node.get("source").asString(),
                new RegimeCode(node.get("regime").asString()),
                List.copyOf(years),
                new Salary(List.copyOf(pay), salary.get("termsIncludeDearnessAllowance").asBoolean(),
                        List.copyOf(tenancies), List.copyOf(contributions), history(salary), payments(salary),
                        policies(salary), journeys(salary), ledger(salary)),
                money(expected, "salaryExemptions"),
                expected.has("limitedBy") ? Optional.of(expected.get("limitedBy").asString()) : Optional.empty(),
                money(expected, "employerContributionCharges"),
                money(expected, "chapterEightDeduction"),
                money(expected, "scheduleXvAggregate"));
    }

    private static List<QualifyingPayment> payments(JsonNode salary) {
        List<QualifyingPayment> payments = new ArrayList<>();
        if (salary.has("qualifyingPayments")) {
            salary.get("qualifyingPayments").propertyNames().forEach(code -> payments.add(new QualifyingPayment(
                    new ComponentCode(code), Money.of(salary.get("qualifyingPayments").get(code).asString()))));
        }
        return List.copyOf(payments);
    }

    private static List<LifeInsurancePolicy> policies(JsonNode salary) {
        List<LifeInsurancePolicy> policies = new ArrayList<>();
        if (salary.has("lifeInsurancePolicies")) {
            salary.get("lifeInsurancePolicies").forEach(policy -> policies.add(new LifeInsurancePolicy(
                    Money.of(policy.get("premiumPaid").asString()), Money.of(policy.get("sumAssured").asString()),
                    LocalDate.parse(policy.get("issuedOn").asString()), false)));
        }
        return List.copyOf(policies);
    }

    private static List<Journey> journeys(JsonNode salary) {
        List<Journey> journeys = new ArrayList<>();
        if (salary.has("leaveTravel")) {
            salary.get("leaveTravel").forEach(journey -> journeys.add(new Journey(
                    LocalDate.parse(journey.get("startedOn").asString()),
                    JourneyMode.valueOf(journey.get("mode").asString()),
                    Money.of(journey.get("amountIncurred").asString()),
                    Optional.of(Money.of(journey.get("fareAdmissible").asString())), Optional.empty())));
        }
        return List.copyOf(journeys);
    }

    /** Each block, by its first calendar year, with the journeys earlier years claimed in it. */
    private static Map<Integer, LeaveTravelBlock> ledger(JsonNode salary) {
        Map<Integer, LeaveTravelBlock> blocks = new LinkedHashMap<>();
        if (salary.has("leaveTravelLedger")) {
            salary.get("leaveTravelLedger").propertyNames().forEach(block -> {
                JsonNode entry = salary.get("leaveTravelLedger").get(block);
                blocks.put(Integer.parseInt(block), new LeaveTravelBlock(entry.get("journeysClaimed").asInt(),
                        entry.get("carriedJourneyClaimed").asBoolean()));
            });
        }
        return Map.copyOf(blocks);
    }

    private static Optional<RetirementFundHistory> history(JsonNode salary) {
        if (!salary.has("retirementFundHistory")) {
            return Optional.empty();
        }
        JsonNode fund = salary.get("retirementFundHistory");
        return Optional.of(new RetirementFundHistory(Money.of(fund.get("earlierExcessContributions").asString()),
                Money.of(fund.get("earlierTaxedAccretions").asString()),
                Money.of(fund.get("incomeAccrued").asString()), Money.of(fund.get("openingBalance").asString()),
                Money.of(fund.get("closingBalance").asString())));
    }

    private static Optional<Money> money(JsonNode expected, String field) {
        return expected.has(field) ? Optional.of(Money.of(expected.get(field).asString())) : Optional.empty();
    }

    @Override
    public String toString() {
        return id;
    }
}
