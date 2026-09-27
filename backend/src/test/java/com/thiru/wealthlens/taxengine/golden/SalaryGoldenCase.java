package com.thiru.wealthlens.taxengine.golden;

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
 * employment provide for dearness allowance, the homes occupied, and the exemption the case expects — copied, never
 * computed (D-104). Run through the shipped pipeline to income under "Salaries".
 *
 * <p>A location is entered as the year's table names it, so a case whose home is listed in one year and not in the
 * other is two files, one per year.
 *
 * @param expectedExemption the case's "Expected" exemption, compared with {@code salary.exemptions}
 * @param expectedLimitedBy the limb or reason the case names, as the pack words it, where the case names one
 */
record SalaryGoldenCase(
        String id,
        String source,
        RegimeCode regime,
        List<TaxYearId> taxYears,
        Salary salary,
        Money expectedExemption,
        Optional<String> expectedLimitedBy) {

    /** What the case enters for the one employment and the homes occupied. */
    record Salary(List<SalaryComponent> pay, boolean termsIncludeDearnessAllowance, List<Tenancy> tenancies) {
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
                        List.copyOf(tenancies)),
                Money.of(expected.get("salaryExemptions").asString()),
                expected.has("limitedBy") ? Optional.of(expected.get("limitedBy").asString()) : Optional.empty());
    }

    @Override
    public String toString() {
        return id;
    }
}
