package com.thiru.wealthlens.taxengine.test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The coverage pin itself (W-327e3, D-693): what it refuses and each thing it allows. */
class ReasonCoverageTest {

    private static final TaxYearId YEAR = TaxYearId.of(2026);

    private static final RuleId RENT_PAID = new RuleId("DED-RENT-PAID");

    private static TraceLine line(RuleId rule) {
        return TraceLine.of(1, new NodeId("chapterviii"), rule, StepKind.DEDUCTION, MoneyValue.of(Money.of("180000")),
                MoneyValue.of(Money.of("60000")), new ClasspathRulePackProvider("rulepacks", Set.of(YEAR),
                        new JsonPackAssembler(LockedInstruments.of(YEAR))).load(YEAR).rule(RENT_PAID).headProvenance());
    }

    @Test
    void require_whenALineCarriesEngineTextAndNoReason_failsNamingTheRuleAndText() {
        // Given — DED-AGNIVEER-OWN's content words no limit, so its text is engine text
        TraceLine unworded = line(new RuleId("DED-AGNIVEER-OWN")).withLimitedBy("the whole amount");

        // When / Then
        assertThatThrownBy(() -> ReasonCoverage.require(List.of(unworded))).isInstanceOf(AssertionError.class)
                .hasMessageContaining("DED-AGNIVEER-OWN").hasMessageContaining("the whole amount");
    }

    @Test
    void require_whenTheTextIsALimitTheContentWords_allowsIt() {
        // Given / When / Then
        assertThatCode(() -> ReasonCoverage.require(List.of(line(RENT_PAID).withLimitedBy("5000 per month"))))
                .doesNotThrowAnyException();
    }

    @Test
    void require_whenTheTextStartsWithALimitTheContentWords_allowsItAsTheRendererDoes() {
        // Given — the renderer matches a limit by prefix, so this line already reads in words
        TraceLine suffixed = line(RENT_PAID).withLimitedBy("5000 per month, on total income before this deduction 450000.00");

        // When / Then
        assertThatCode(() -> ReasonCoverage.require(List.of(suffixed))).doesNotThrowAnyException();
    }

    @Test
    void require_whenTheLineCarriesAReason_allowsItsText() {
        // Given
        TraceLine reasoned = line(new RuleId("DED-AGNIVEER-OWN")).withLimitedBy("the whole amount")
                .withReason(new Reason(ReasonCodes.ALL_DEDUCTED, Map.of()));

        // When / Then
        assertThatCode(() -> ReasonCoverage.require(List.of(reasoned))).doesNotThrowAnyException();
    }

    @Test
    void require_whenNoShippedYearExplainsTheRule_leavesItToShowItsRuleId() {
        // Given — a test's own rule, which F37.2 shows by its id
        TraceLine fixture = line(new RuleId("SYN-STANDARD-DEDUCTION")).withLimitedBy("statutory amount");

        // When / Then
        assertThatCode(() -> ReasonCoverage.require(List.of(fixture))).doesNotThrowAnyException();
    }
}
