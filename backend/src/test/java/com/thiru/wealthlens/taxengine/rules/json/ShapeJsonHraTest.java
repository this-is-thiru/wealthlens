package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.AmountInExcess;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBaseByLocation;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** HRA's two shapes read from a rule row (D-099). Parse inputs, not law. */
class ShapeJsonHraTest {

    private static final String PATH = "law/rules/X.json.versions[0].cap";

    @Test
    void cap_whenAmountInExcess_readsTheAmountAndTheThreshold() {
        // When
        CapShape cap = ShapeJson.cap(PackJsonTest.json("""
                { "type": "AmountInExcess", "amount": "RENT_PAID",
                  "threshold": { "type": "PercentOfBase", "percent": "10", "base": "SALARY" } }"""), PATH);

        // Then
        assertThat(cap).isEqualTo(new AmountInExcess(new BaseId("RENT_PAID"),
                new PercentOfBase(Rate.percent("10"), new BaseId("SALARY"))));
    }

    @Test
    void cap_whenPercentOfBaseByLocation_readsBandsAndOtherwise() {
        // When
        CapShape cap = ShapeJson.cap(PackJsonTest.json("""
                { "type": "PercentOfBaseByLocation", "base": "SALARY", "location": "LOCATION",
                  "bands": [ { "locations": ["MUMBAI", "DELHI"], "percent": "50" } ],
                  "otherwise": "40" }"""), PATH);

        // Then
        assertThat(cap).isEqualTo(new PercentOfBaseByLocation(new BaseId("SALARY"), new BaseId("LOCATION"),
                List.of(new PercentOfBaseByLocation.Band(Set.of("MUMBAI", "DELHI"), Rate.percent("50"))),
                Rate.percent("40")));
    }

    @Test
    void cap_whenAmountInExcessHasNoThreshold_isRefusedNamingThePath() {
        assertThatThrownBy(() -> ShapeJson.cap(PackJsonTest.json("""
                        { "type": "AmountInExcess", "amount": "RENT_PAID" }"""), PATH))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining(PATH)
                .hasMessageContaining("threshold");
    }
}
