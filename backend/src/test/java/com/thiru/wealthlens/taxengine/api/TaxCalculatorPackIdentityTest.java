package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What a stored computation is compared against (W-138): read here from the lock file itself, not through the code under test. */
class TaxCalculatorPackIdentityTest {

    private static final TaxYearId TY_2026 = TaxYearId.of(2026);
    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(TY_2026)));

    private static JsonNode lock() throws IOException {
        try (InputStream in = TaxCalculatorPackIdentityTest.class.getResourceAsStream("/rulepacks/2026-27.lock.json")) {
            return JsonMapper.builder().build().readTree(in);
        }
    }

    @Test
    void packVersions_whenTheYearShips_isItsLawAndExecLayersOnly() throws IOException {
        // Given
        JsonNode layers = lock().get("layers");

        // When
        String versions = String.join(" ", CALCULATOR.packVersions(TY_2026).orElseThrow());

        // Then
        assertThat(CALCULATOR.packVersions(TY_2026).orElseThrow()).hasSize(2);
        assertThat(versions).contains(layers.get("law").get("version").asString(), layers.get("exec").get("version").asString())
                .doesNotContain(layers.get("forms").get("version").asString(), layers.get("content").get("version").asString());
    }

    @Test
    void packVersions_whenNoPackShipsForTheYear_isEmpty() {
        // When / Then
        assertThat(CALCULATOR.packVersions(TaxYearId.of(2019))).isEmpty();
    }

    @Test
    void ruleChecksums_whenAskedForRulesHeldAndNot_isTheLocksChecksumForThoseHeld() throws IOException {
        // Given
        String expected = lock().get("rules").get("ADV-INSTALMENTS").asString();

        // When / Then — a rule the pack does not hold is left out
        assertThat(CALCULATOR.ruleChecksums(TY_2026, Set.of("ADV-INSTALMENTS", "NO-SUCH-RULE")))
                .isEqualTo(Map.of("ADV-INSTALMENTS", expected));
    }
}
