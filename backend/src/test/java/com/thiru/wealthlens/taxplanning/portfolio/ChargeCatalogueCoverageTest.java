package com.thiru.wealthlens.taxplanning.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Charge;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.ChargeSide;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.CapitalGainsHarness;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every charge the catalogue can put on a trade reaches a row of {@code RS-CG-CHARGE} on both sides in every shipped
 * year (W-162a, D-375 C3a): the engine refuses a charge no row covers, so a code added to the catalogue without its row
 * fails here, at build time, rather than in a user's computation. Account-level charges (category
 * {@code SUBSCRIPTION}) are billed on a cycle, never on a trade, and need no row.
 */
class ChargeCatalogueCoverageTest {

    private static final String CATALOGUE = "data/charges/charge-catalogue.json";

    private static final String ACCOUNT_LEVEL = "SUBSCRIPTION";

    private static JsonNode catalogue() throws IOException {
        try (InputStream in = ChargeCatalogueCoverageTest.class.getClassLoader().getResourceAsStream(CATALOGUE)) {
            assertThat(in).as(CATALOGUE).isNotNull();
            return JsonMapper.builder().build().readTree(in);
        }
    }

    static Stream<Arguments> tradeChargesBySideAndYear() throws IOException {
        List<Arguments> cases = new ArrayList<>();
        for (JsonNode entry : catalogue()) {
            if (ACCOUNT_LEVEL.equals(entry.get("category").asString())) {
                continue;
            }
            for (TaxYearId year : ClasspathRulePackProvider.shippedYears("rulepacks")) {
                for (ChargeSide side : ChargeSide.values()) {
                    cases.add(Arguments.of(entry.get("code").asString(), side, year.startYear()));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} on the {1}, TY {2}")
    @MethodSource("tradeChargesBySideAndYear")
    void evaluate_whenTheCatalogueCodeIsCharged_aRowCoversIt(String code, ChargeSide side, int year) {
        // Given — listed equity, long-term, with this one charge
        LocalDate acquiredOn = LocalDate.parse("2020-06-01");
        BucketFacts facts = new BucketFacts("EQUITY", "PAID", "PAID", "PURCHASE", Optional.empty(), acquiredOn,
                acquiredOn.plusYears(2), SideVenue.UNRECORDED, new SideVenue(Optional.of("NSE"), Optional.empty()));
        CapitalGainsInput input = CapitalGainsHarness.inputOf(new CapitalGainsInput.Bucket(Money.of("1000"),
                Money.of("900"), List.of(new Charge(side, code, Money.of("1"))), facts, Optional.empty()));

        // When / Then
        assertThatCode(() -> CapitalGainsHarness.run(year, input)).doesNotThrowAnyException();
    }

    @Test
    void catalogue_whenRead_holdsNoCodeNamedAsTheHandEnteredCharge() throws IOException {
        // When
        List<String> codes = new ArrayList<>();
        catalogue().forEach(entry -> codes.add(entry.get("code").asString()));

        // Then — the adapter passes a hand-entered charge as MISC; a catalogue code of that name would be read as one
        assertThat(codes).isNotEmpty().doesNotContain(CapitalGainsAdapter.MISC);
    }
}
