package com.thiru.wealthlens.taxengine.golden;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
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
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * One {@code DR 08} case stated as salary facts rather than a total income: the pay, whether the terms of
 * employment provide for dearness allowance, the homes occupied, the employer's retirement contributions and the
 * funds' history, and what the case expects — copied, never computed (D-104, D-128). Run through the shipped pipeline
 * to income under "Salaries".
 *
 * <p>A location is entered as the year's table names it, so a case whose home is listed in one year and not in the
 * other is two files, one per year.
 *
 * @param expectedExemption the case's "Expected" exemption, compared with {@code salary.exemptions}, where it states one
 * @param expectedLimitedBy the limb or reason the case names, as the pack words it, where the case names one
 * @param expectedEmployerContributionCharges the employer contributions the case adds to salary, compared with
 *     {@code employer.contrib.charges}, where it states them
 */
record SalaryGoldenCase(
        String id,
        String source,
        RegimeCode regime,
        List<TaxYearId> taxYears,
        Salary salary,
        Optional<Money> expectedExemption,
        Optional<String> expectedLimitedBy,
        Optional<Money> expectedEmployerContributionCharges) {

    /** What the case enters for the one employment, the homes occupied and the employer's retirement funds. */
    record Salary(List<SalaryComponent> pay, boolean termsIncludeDearnessAllowance, List<Tenancy> tenancies,
            List<EmployerFundContribution> employerContributions,
            Optional<RetirementFundHistory> retirementFundHistory) {
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
                        List.copyOf(tenancies), List.copyOf(contributions), history(salary)),
                money(expected, "salaryExemptions"),
                expected.has("limitedBy") ? Optional.of(expected.get("limitedBy").asString()) : Optional.empty(),
                money(expected, "employerContributionCharges"));
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
