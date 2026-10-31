package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.SalaryBaseInclusion;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import org.junit.jupiter.api.Test;

/**
 * A component's salary-base entries (D-092): each names the base and whether the component counts in it only
 * where the terms of employment provide. The codes and rules are parse inputs, not the law's.
 */
class RegistryJsonTest {

    private static final String PATH = "exec/components/DA.json";

    private static ComponentDefinition componentWith(String salaryBaseFor) {
        return RegistryJson.component(PackJsonTest.json("""
                {
                  "code": "DA",
                  "displayName": "Dearness allowance",
                  "head": "SALARIES",
                  "kind": "SALARY",
                  "cashNature": "CASH",
                  "treatment": "TAXABLE",
                  "feedsInto": ["SAL-SALARY-INCLUDES"],
                  "salaryBaseFor": %s,
                  "lifecycle": {
                    "validity": { "from": "2025-04-01" },
                    "onWithdrawal": { "type": "HistoricalOnly" } },
                  "provenance": {
                    "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
                    "sourceDescription": "test fixture",
                    "accessedOn": "2026-09-27",
                    "verificationLevel": "SOURCE_CHECKED" }
                }""".formatted(salaryBaseFor)), PATH);
    }

    @Test
    void component_whenSalaryBaseEntries_readsEachWithItsTermsCondition() {
        // When
        ComponentDefinition component = componentWith("""
                [ { "rule": "SAL-SALARY-FOR-HRA", "onlyWhereTermsProvide": true },
                  { "rule": "SAL-SALARY-FOR-GRATUITY", "onlyWhereTermsProvide": false } ]""");

        // Then
        assertThat(component.salaryBaseFor()).containsExactlyInAnyOrder(
                new SalaryBaseInclusion(new RuleId("SAL-SALARY-FOR-HRA"), true),
                new SalaryBaseInclusion(new RuleId("SAL-SALARY-FOR-GRATUITY"), false));
    }

    @Test
    void component_whenNoSalaryBaseEntries_hasNone() {
        // When
        ComponentDefinition component = componentWith("[]");

        // Then
        assertThat(component.salaryBaseFor()).isEmpty();
    }

    @Test
    void component_whenTheTermsConditionIsAbsent_isRefusedNamingTheField() {
        // Given — no default: whether a component counts only on the terms is the law's, never assumed
        String silent = """
                [ { "rule": "SAL-SALARY-FOR-HRA" } ]""";

        // When / Then
        assertThatThrownBy(() -> componentWith(silent))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("onlyWhereTermsProvide");
    }

    @Test
    void component_whenAnEntryIsABareRuleId_isRefused() {
        // When / Then
        assertThatThrownBy(() -> componentWith("[ \"SAL-SALARY-FOR-HRA\" ]"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("salaryBaseFor");
    }

    @Test
    void component_whenOneBaseIsNamedTwice_isRefusedNamingIt() {
        // Given
        String twice = """
                [ { "rule": "SAL-SALARY-FOR-HRA", "onlyWhereTermsProvide": true },
                  { "rule": "SAL-SALARY-FOR-HRA", "onlyWhereTermsProvide": false } ]""";

        // When / Then
        assertThatThrownBy(() -> componentWith(twice))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("SAL-SALARY-FOR-HRA");
    }
}
