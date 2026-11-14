package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSelection;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSet;
import com.thiru.wealthlens.taxengine.rules.pack.SelectionGround;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The sets a law file declares, each with the rows that name it (W-088m1, D-252). */
class RuleSetJsonTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final RuleSetId REBATES = new RuleSetId("RS-REBATE");
    private static final RuleSetId STANDARD = new RuleSetId("RS-SAL-STANDARD-DEDUCTION");

    private static JsonNode rates(String rules, String sets) {
        return MAPPER.readTree("{\"rules\": [" + rules + "], \"ruleSets\": [" + sets + "]}");
    }

    private static final String SETS = "{\"id\": \"RS-REBATE\", \"selection\": \"ALL_THAT_APPLY\", \"ground\": \"REGIME\"},"
            + "{\"id\": \"RS-SAL-STANDARD-DEDUCTION\", \"selection\": \"EXACTLY_ONE\", \"ground\": \"REGIME\"}";

    @Test
    void ruleSets_whenRowsNameTheirSets_givesEachSetItsRowsInItsOrder() {
        // Given — the rebates listed out of order, placed by position; the deductions ordered by id
        JsonNode rates = rates("{\"ruleId\": \"REBATE-B\", \"ruleSet\": \"RS-REBATE\", \"ruleSetPosition\": 2},"
                + "{\"ruleId\": \"REBATE-A\", \"ruleSet\": \"RS-REBATE\", \"ruleSetPosition\": 1},"
                + "{\"ruleId\": \"SAL-STD-ZZZ\", \"ruleSet\": \"RS-SAL-STANDARD-DEDUCTION\"},"
                + "{\"ruleId\": \"SAL-STD-AAA\", \"ruleSet\": \"RS-SAL-STANDARD-DEDUCTION\"}", SETS);

        // When
        Map<RuleSetId, RuleSet> sets = RuleSetJson.ruleSets(rates, "law/rates.json");

        // Then
        assertThat(sets.get(REBATES).members()).containsExactly(new RuleId("REBATE-A"), new RuleId("REBATE-B"));
        assertThat(sets.get(REBATES).selection()).isEqualTo(RuleSelection.ALL_THAT_APPLY);
        assertThat(sets.get(STANDARD).members()).containsExactly(new RuleId("SAL-STD-AAA"), new RuleId("SAL-STD-ZZZ"));
        assertThat(sets.get(STANDARD).ground()).isEqualTo(SelectionGround.REGIME);
    }

    @Test
    void ruleSets_whenARowNamesAnUndeclaredSet_isRefused() {
        // When / Then
        assertThatThrownBy(() -> RuleSetJson.ruleSets(
                        rates("{\"ruleId\": \"SAL-STD-AAA\", \"ruleSet\": \"RS-NOWHERE\"}", SETS), "law/rates.json"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("names rule set RS-NOWHERE, which law/rates.json does not declare");
    }

    @Test
    void ruleSets_whenAnOrderedSetsRowHasNoPosition_isRefused() {
        // When / Then
        assertThatThrownBy(() -> RuleSetJson.ruleSets(
                        rates("{\"ruleId\": \"REBATE-A\", \"ruleSet\": \"RS-REBATE\"}", SETS), "law/rates.json"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("needs a ruleSetPosition");
    }

    @Test
    void ruleSets_whenAnUnorderedSetsRowStatesAPosition_isRefused() {
        // When / Then
        assertThatThrownBy(() -> RuleSetJson.ruleSets(rates(
                        "{\"ruleId\": \"SAL-STD-AAA\", \"ruleSet\": \"RS-SAL-STANDARD-DEDUCTION\", \"ruleSetPosition\": 1}",
                        SETS), "law/rates.json"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("may not state a ruleSetPosition");
    }

    @Test
    void ruleSets_whenTwoRowsShareAPosition_isRefusedNamingBoth() {
        // When / Then
        assertThatThrownBy(() -> RuleSetJson.ruleSets(
                        rates("{\"ruleId\": \"REBATE-A\", \"ruleSet\": \"RS-REBATE\", \"ruleSetPosition\": 1},"
                                + "{\"ruleId\": \"REBATE-B\", \"ruleSet\": \"RS-REBATE\", \"ruleSetPosition\": 1}", SETS),
                        "law/rates.json"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("REBATE-A and REBATE-B at the same position");
    }
}
