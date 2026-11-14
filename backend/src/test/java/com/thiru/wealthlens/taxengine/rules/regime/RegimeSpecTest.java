package com.thiru.wealthlens.taxengine.rules.regime;

import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.refusal;
import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.withComponent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link RegimeSpec} refuses, one part made bad at a time; each message is the record's own (W-240c). */
class RegimeSpecTest {

    private static final RegimeSpec VALID = RegimeFixtures.regimeWithBehaviour(null);

    @Test
    void construct_whenTheOptionalSetsAreNull_readsThemAsEmpty() throws Throwable {
        // When — null is how a pack omits them (RegimeSpec.java:66-69)
        RegimeSpec omitted = withComponent(withComponent(withComponent(VALID, "disallowances", null), "eligibility",
                null), "behaviour", null);

        // Then
        assertThat(omitted.disallowances()).isEmpty();
        assertThat(omitted.eligibility()).isEmpty();
        assertThat(omitted.behaviour()).isEmpty();
    }

    @Test
    void availableTo_whenThePersonTypeIsNotOneItAppliesTo_isFalseAndOtherwiseFollowsTheLifecycle() {
        // Given — the fixture applies to INDIVIDUAL only, its entry window open from 1-4-2024 with no end
        TaxYearId year = TaxYearId.of(2026);

        // When / Then — RegimeSpec.java:121-128
        assertThat(VALID.availableTo(PersonType.HUF, year)).isFalse();
        assertThat(VALID.openToNewEntrants(PersonType.HUF, year)).isFalse();
        assertThat(VALID.openToNewEntrants(PersonType.INDIVIDUAL, year)).isTrue();
        // 2023-24 begins on 1-4-2023, before the entry window opens
        assertThat(VALID.openToNewEntrants(PersonType.INDIVIDUAL, TaxYearId.of(2023))).isFalse();
    }

    @Test
    void construct_whenNotTheDefault_isAccepted() throws Throwable {
        // When
        RegimeSpec optional = withComponent(VALID, "isDefault", false);

        // Then
        assertThat(optional.isDefault()).isFalse();
    }

    static Stream<Arguments> refusals() throws Throwable {
        String declarations = "must declare its election protocol, lifecycle and pipeline";
        String provisions = "must state its place in both Acts";
        return Stream.of(
                refusal("code null", () -> withComponent(VALID, "code", null),
                        "A regime must have a code"),
                refusal("displayName null", () -> withComponent(VALID, "displayName", null),
                        "must have a display name"),
                refusal("displayName blank", () -> withComponent(VALID, "displayName", " "),
                        "must have a display name"),
                refusal("provision1961 null", () -> withComponent(VALID, "provision1961", null),
                        provisions),
                refusal("provision2025 null", () -> withComponent(VALID, "provision2025", null),
                        provisions),
                refusal("rateTable null", () -> withComponent(VALID, "rateTable", null),
                        "must name its rate table"),
                refusal("election null", () -> withComponent(VALID, "election", null),
                        declarations),
                refusal("lifecycle null", () -> withComponent(VALID, "lifecycle", null),
                        declarations),
                refusal("pipeline null", () -> withComponent(VALID, "pipeline", null),
                        declarations),
                refusal("provenance null", () -> withComponent(VALID, "provenance", null),
                        "must say where it was read from"),
                refusal("appliesTo null, so nobody", () -> withComponent(VALID, "appliesTo", null),
                        "applies to nobody"),
                refusal("appliesTo empty", () -> withComponent(VALID, "appliesTo", Set.of()),
                        "applies to nobody"),
                refusal("default regime that must be opted into", () -> withComponent(VALID, "election", withComponent(VALID.election(), "kind", ElectionKind.OPT_IN)),
                        "is marked isDefault but its election kind is OPT_IN"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
