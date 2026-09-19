package com.thiru.wealthlens.taxrules.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One positive and one negative case per predicate, plus the two behaviours the algebra's usefulness
 * rests on: a missing fact throws rather than denying, and a failing condition can say why.
 *
 * <p>W-021b and W-021c extend this class with the amount, structure and composition predicates.
 */
class ConditionTest {

    private static ConditionContext resident(int age) {
        return ConditionContext.residentIndividualAged(age);
    }

    private static ConditionContext nonResident(int age) {
        return new ConditionContext(
                false, PersonType.INDIVIDUAL, Map.of(AgeSubject.SELF, age), Map.of());
    }

    @Nested
    @DisplayName("IsResident")
    class Residency {

        @Test
        void holds_whenResident() {
            assertThat(new IsResident().holds(resident(35))).isTrue();
        }

        @Test
        void doesNotHold_whenNotResident() {
            // RATE-SLAB-AGE-001: the higher old-regime exemptions are for residents only
            assertThat(new IsResident().holds(nonResident(65))).isFalse();
        }

        @Test
        void describe_readsAsAReasonAClaimWasRefused() {
            assertThat(new IsResident().describe()).isEqualTo("the assessee is resident");
        }
    }

    @Nested
    @DisplayName("AgeAtLeast — the subject matters")
    class Age {

        @Test
        void holds_atExactlyTheThreshold() {
            // "60 or over" includes 60
            assertThat(new AgeAtLeast(60, AgeSubject.SELF).holds(resident(60))).isTrue();
        }

        @Test
        void doesNotHold_oneYearBelow() {
            assertThat(new AgeAtLeast(60, AgeSubject.SELF).holds(resident(59))).isFalse();
        }

        @Test
        void selfAndInsuredPersonAreDifferentQuestions() {
            // Given — DED-80D-007: the taxpayer is 45, the insured parent is 70
            ConditionContext ctx = new ConditionContext(
                    true,
                    PersonType.INDIVIDUAL,
                    Map.of(AgeSubject.SELF, 45, AgeSubject.INSURED_PERSON, 70),
                    Map.of());

            // Then — without the subject field these two would be the same predicate,
            // and the rule would collapse to a test of the taxpayer's own age
            assertThat(new AgeAtLeast(60, AgeSubject.SELF).holds(ctx)).isFalse();
            assertThat(new AgeAtLeast(60, AgeSubject.INSURED_PERSON).holds(ctx)).isTrue();
        }

        @Test
        void holds_whenTheSubjectsAgeWasNeverSupplied_throwsRatherThanDenying() {
            // D-NOSILENT: a denial that looks like a legitimate result is worse than an error
            assertThatThrownBy(() -> new AgeAtLeast(60, AgeSubject.INSURED_PERSON).holds(resident(70)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INSURED_PERSON");
        }

        @Test
        void describe_namesTheSubject() {
            assertThat(new AgeAtLeast(60, AgeSubject.SELF).describe()).isEqualTo("you are 60 or over");
            assertThat(new AgeAtLeast(60, AgeSubject.INSURED_PERSON).describe())
                    .isEqualTo("the insured person is 60 or over");
        }

        @Test
        void construction_rejectsANegativeThresholdAndAMissingSubject() {
            assertThatThrownBy(() -> new AgeAtLeast(-1, AgeSubject.SELF))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AgeAtLeast(60, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("whose age");
        }
    }

    @Nested
    @DisplayName("HasPersonType")
    class Kind {

        @Test
        void holds_forTheDeclaredType() {
            assertThat(new HasPersonType(Set.of(PersonType.INDIVIDUAL)).holds(resident(35))).isTrue();
        }

        @Test
        void construction_whenTheSetIsEmpty_isRejected() {
            // A rule that applies to nobody is a pack mistake, not a rule
            assertThatThrownBy(() -> new HasPersonType(Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("applies to nobody");
        }
    }

    @Nested
    @DisplayName("IsCertifiedDisabled — a minimum, not an equality")
    class Disability {

        private static ConditionContext certified(DisabilitySeverity severity) {
            return new ConditionContext(
                    true,
                    PersonType.INDIVIDUAL,
                    Map.of(AgeSubject.SELF, 40),
                    Map.of(AgeSubject.SELF, severity));
        }

        @Test
        void holds_atTheSameSeverity() {
            assertThat(new IsCertifiedDisabled(DisabilitySeverity.DISABILITY)
                            .holds(certified(DisabilitySeverity.DISABILITY)))
                    .isTrue();
        }

        @Test
        void holds_whenCertifiedMoreSeverely() {
            // DED-80U-001's ₹1,25,000 rung does not stop the ₹75,000 rung's condition being met
            assertThat(new IsCertifiedDisabled(DisabilitySeverity.DISABILITY)
                            .holds(certified(DisabilitySeverity.SEVERE_DISABILITY)))
                    .isTrue();
        }

        @Test
        void doesNotHold_whenCertifiedLessSeverely() {
            assertThat(new IsCertifiedDisabled(DisabilitySeverity.SEVERE_DISABILITY)
                            .holds(certified(DisabilitySeverity.DISABILITY)))
                    .isFalse();
        }

        @Test
        void doesNotHold_whenNoCertificateWasRecorded() {
            // s.154(2) requires the certificate with the return, so its absence IS the answer —
            // unlike a missing age, which is a gap in what the taxpayer told us
            assertThat(new IsCertifiedDisabled(DisabilitySeverity.DISABILITY).holds(resident(40)))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("ConditionContext")
    class Context {

        @Test
        void maps_areDefensivelyCopied() {
            Map<AgeSubject, Integer> mutable = new java.util.HashMap<>();
            mutable.put(AgeSubject.SELF, 60);
            ConditionContext ctx =
                    new ConditionContext(true, PersonType.INDIVIDUAL, mutable, Map.of());

            mutable.put(AgeSubject.SELF, 20);

            assertThat(ctx.age(AgeSubject.SELF)).isEqualTo(60);
        }

        @Test
        void construction_whenPersonTypeIsMissing_isRejected() {
            assertThatThrownBy(() -> new ConditionContext(true, null, Map.of(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
