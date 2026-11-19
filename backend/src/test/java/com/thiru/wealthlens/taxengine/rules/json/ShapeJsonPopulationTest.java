package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBaseByPopulation;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owned accommodation's population shape read from a rule row (D-258). Parse inputs, not law. */
class ShapeJsonPopulationTest {

    @Test
    void cap_whenPercentOfBaseByPopulation_readsBandsAndOtherwise() {
        // When
        CapShape cap = ShapeJson.cap(PackJsonTest.json("""
                { "type": "PercentOfBaseByPopulation", "base": "SALARY", "population": "POPULATION",
                  "bands": [ { "exceeding": 4000000, "percent": "10" }, { "exceeding": 1500000, "percent": "7.5" } ],
                  "otherwise": "5" }"""), "law/rules/X.json.versions[0].cap");

        // Then
        assertThat(cap).isEqualTo(new PercentOfBaseByPopulation(new BaseId("SALARY"), new BaseId("POPULATION"),
                List.of(new PercentOfBaseByPopulation.Band(4_000_000, Rate.percent("10")),
                        new PercentOfBaseByPopulation.Band(1_500_000, Rate.percent("7.5"))),
                Rate.percent("5")));
    }
}
