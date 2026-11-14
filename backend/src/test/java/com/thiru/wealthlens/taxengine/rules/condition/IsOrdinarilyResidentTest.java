package com.thiru.wealthlens.taxengine.rules.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * r.164(2)'s "a resident other than not ordinarily resident" (W-090c1a): only {@code RESIDENT} holds, while
 * {@link IsResident} still counts RNOR as resident (S2 s.5(1)(c)).
 */
class IsOrdinarilyResidentTest {

    private static ConditionContext living(ResidentialStatus residence) {
        return new ConditionContext(residence, PersonType.INDIVIDUAL, Map.of(), Map.of());
    }

    @Test
    void holds_whenResident_holds() {
        assertThat(new IsOrdinarilyResident().holds(living(ResidentialStatus.RESIDENT))).isTrue();
    }

    @Test
    void holds_whenNotOrdinarilyResident_fails() {
        assertThat(new IsOrdinarilyResident().holds(living(ResidentialStatus.RNOR))).isFalse();
    }

    @Test
    void holds_whenNonResident_fails() {
        assertThat(new IsOrdinarilyResident().holds(living(ResidentialStatus.NON_RESIDENT))).isFalse();
    }

    @Test
    void isResident_whenNotOrdinarilyResident_stillHolds() {
        // Given — s.5(1)(c) scopes RNOR inside "a person, who is a resident"; the narrower test is r.164's alone
        ConditionContext rnor = living(ResidentialStatus.RNOR);

        // When / Then
        assertThat(new IsResident().holds(rnor)).isTrue();
        assertThat(new IsResident().holds(living(ResidentialStatus.NON_RESIDENT))).isFalse();
    }
}
