package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link NodeContext#inRegime} against the shipped packs (D-165): a rule the regime forbids is {@code Disallowed}
 * naming its set, a row written for other regimes is {@code ExcludedByRow}, and anything else applies.
 */
class RegimeVerdictTest {

    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");

    private static RegimeVerdict verdict(int year, RegimeCode regime, RuleId rule) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        NodeContext ctx = NodeContext.forRun(pack, pack.regimes().get(regime), NodeHarness.plainTaxpayer());
        return ctx.inRegime(rule, pack.rule(rule).versions().getFirst());
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void inRegime_whenTheRegimesSetListsTheRule_isDisallowedNamingTheSetAndItsBasis(int year) {
        // When
        RegimeVerdict verdict = verdict(year, NEW_TAX_REGIME, RuleIds.SAL_TAX_ON_EMPLOYMENT);

        // Then — the set's version for the year rests on s.115BAC(2)(i) (TY 2025-26) or s.202(2)(a)
        assertThat(verdict.applies()).isFalse();
        assertThat(verdict).isInstanceOfSatisfying(RegimeVerdict.Disallowed.class, disallowed -> {
            assertThat(disallowed.set().id()).isEqualTo(new DisallowanceSetId("DIS-NEW-TAX-REGIME-NO-EXEMPTION"));
            assertThat(disallowed.reason())
                    .startsWith("disallowed in the NEW_TAX_REGIME regime: ")
                    .contains(year == 2025 ? "s.115BAC(2)(i)" : "s.202(2)(a)")
                    .endsWith("(" + RuleIds.REG_NEW_TAX_REGIME_NO_EXEMPTION + ")");
        });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void inRegime_whenTheRowIsWrittenForOtherRegimes_isExcludedByTheRow(int year) {
        // When — s.19 Table Sl. 2(b) keys the amount on the regime; nothing forbids it
        RegimeVerdict verdict = verdict(year, NEW_TAX_REGIME, RuleIds.SAL_STANDARD_DEDUCTION_ANY_OTHER_CASE);

        // Then
        assertThat(verdict).isInstanceOf(RegimeVerdict.ExcludedByRow.class);
        assertThat(verdict.applies()).isFalse();
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void inRegime_whenTheRegimeReferencesNoSetListingIt_applies(int year) {
        // When — the regime outside s.202 references no disallowance set
        RegimeVerdict verdict = verdict(year, FIRST_SCHEDULE_RATES, RuleIds.SAL_TAX_ON_EMPLOYMENT);

        // Then
        assertThat(verdict).isInstanceOf(RegimeVerdict.Applies.class);
        assertThat(verdict.applies()).isTrue();
    }
}
