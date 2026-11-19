package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code ComputedTax}'s JSON is a stored format: {@code DraftComputationService} keeps it as {@code resultJson} and reads it back
 * (W-333 C2a). The golden file is T8-1's computation as the 18-component record wrote it, captured before W-333, with the fresh
 * computation id and receipt fixed: here the expected value is the old code's own output, because the point is "unchanged".
 * Field for field, not byte for byte: the groups write their fields in group order, and {@code @JsonPropertyOrder} cannot reorder
 * unwrapped fields (tried in W-333; the order test failed with and without it). Order is no part of what JSON means.
 */
class ComputedTaxJsonTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(TaxYearId.of(2026))));

    private static String golden() throws IOException {
        try (InputStream in = ComputedTaxJsonTest.class.getResourceAsStream("/taxplanning/computed-tax-t8-1.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ObjectNode written() {
        ObjectNode tree = (ObjectNode) MAPPER.readTree(MAPPER.writeValueAsString(CALCULATOR.compute(Fixtures.priya())));
        tree.put("computationId", "fixed");
        tree.put("receipt", "fixed");
        return tree;
    }

    @Test
    void write_whenT8_1IsComputed_writesTheSameFieldsAndValuesAsBeforeW333() throws IOException {
        // When / Then — field for field, value for value
        assertThat(written()).isEqualTo(MAPPER.readTree(golden()));
    }

    @Test
    void read_whenAComputationWasStoredBeforeW333_readsItBackToTheSameJson() throws IOException {
        // When
        ComputedTax read = MAPPER.readValue(golden(), ComputedTax.class);

        // Then
        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(read))).isEqualTo(MAPPER.readTree(golden()));
    }

    @Test
    void read_whenAComputationWasStoredBeforeW130bAndW119c_readsItWithoutTheLaterFields() throws IOException {
        // Given — before W-130b / W-130d there was no chapterEight or otherHeads, and before W-119c's follow-up no notices
        ObjectNode older = (ObjectNode) MAPPER.readTree(golden());
        older.remove(java.util.List.of("chapterEight", "otherHeads", "notices"));

        // When
        JsonNode reread = MAPPER.readTree(MAPPER.writeValueAsString(MAPPER.readValue(MAPPER.writeValueAsString(older), ComputedTax.class)));

        // Then — the rest unchanged, notices none, the two totals absent
        assertThat(reread.get("bill")).isEqualTo(older.get("bill"));
        assertThat(reread.get("trace")).isEqualTo(older.get("trace"));
        assertThat(reread.get("notices")).isEqualTo(MAPPER.readTree("[]"));
        assertThat(reread.get("chapterEight").isNull()).isTrue();
    }
}
