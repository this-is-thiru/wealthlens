package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Member;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Relationship;
import com.thiru.wealthlens.taxengine.input.TaxpayerFacts.ResidentialStatus;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The facts the engine is given about a person, and the history behind them.
 */
class TaxpayerFactsTest {

    private static final LocalDate DOB = LocalDate.of(1966, 11, 20);

    private static TaxpayerFacts individual() {
        return new TaxpayerFacts(
                DOB,
                ResidentialStatus.RESIDENT,
                Set.of(PersonType.INDIVIDUAL),
                Optional.empty(),
                Optional.empty());
    }

    private static Member child(LocalDate dateOfBirth) {
        return new Member(
                Relationship.CHILD,
                dateOfBirth,
                true,
                Optional.empty(),
                Optional.empty(),
                false,
                false);
    }

    @Nested
    @DisplayName("TaxpayerFacts — a projection, carrying dates rather than derived facts")
    class Taxpayer {

        @Test
        void theDateOfBirthIsCarriedRatherThanAnAge() {
            // D-025. F2's senior-citizen test is "at any time during the previous year", which an
            // age cannot answer: someone turning 60 in November is a senior citizen for that whole
            // year, and an age taken at any single instant says otherwise for half of it.
            assertThat(individual().dateOfBirth()).isEqualTo(DOB);
            assertThat(TaxpayerFacts.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .doesNotContain("age");
        }

        @Test
        void residenceHasThreeStatesRatherThanTwo() {
            // ConditionContext collapses these to boolean resident, which is lossy: RNOR is
            // resident for some provisions and not others. The projection keeps what was known.
            assertThat(ResidentialStatus.values())
                    .containsExactly(
                            ResidentialStatus.RESIDENT,
                            ResidentialStatus.RNOR,
                            ResidentialStatus.NON_RESIDENT);
        }

        @Test
        void thePersonTypeSetIsDefensivelyCopied() {
            Set<PersonType> mutable = new HashSet<>();
            mutable.add(PersonType.INDIVIDUAL);
            TaxpayerFacts facts = new TaxpayerFacts(
                    DOB, ResidentialStatus.RESIDENT, mutable, Optional.empty(), Optional.empty());

            // PersonType has one constant today (rule B2 — the family lists only what exists), so
            // the mutation that proves the copy is a removal rather than an addition.
            mutable.clear();

            assertThat(facts.personTypes()).containsExactly(PersonType.INDIVIDUAL);
        }

        @Test
        void anEmptyPersonTypeSetIsRejectedBecauseItMakesEveryRegimeInapplicable() {
            assertThatThrownBy(() -> new TaxpayerFacts(
                            DOB,
                            ResidentialStatus.RESIDENT,
                            Set.of(),
                            Optional.empty(),
                            Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("regime eligibility");
        }

        @Test
        void carriesTheCertifiedSeverityAndNotTheCertificate() {
            // DED-80U-001 and DED-80DD-001 read the severity; nothing reads the certificate.
            TaxpayerFacts facts = new TaxpayerFacts(
                    DOB,
                    ResidentialStatus.RESIDENT,
                    Set.of(PersonType.INDIVIDUAL),
                    Optional.of(DisabilitySeverity.SEVERE_DISABILITY),
                    Optional.empty());

            assertThat(facts.disability()).contains(DisabilitySeverity.SEVERE_DISABILITY);
        }

        @Test
        void theEngineIsGivenNoPan() {
            // A3 says "PAN, DOB, residence". No rule computes on a PAN and O1.1 wants it masked
            // wherever it is logged, so it does not enter the engine at all.
            assertThat(TaxpayerFacts.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .containsExactly(
                            "dateOfBirth",
                            "residentialStatus",
                            "personTypes",
                            "disability",
                            "dateOfDeath");
        }

        @Test
        void aDeathBeforeBirthIsRejected() {
            assertThatThrownBy(() -> new TaxpayerFacts(
                            DOB,
                            ResidentialStatus.RESIDENT,
                            Set.of(PersonType.INDIVIDUAL),
                            Optional.empty(),
                            Optional.of(DOB.minusDays(1))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("precedes");
        }

        @Test
        void aNullOptionalIsRejectedRatherThanTreatedAsAbsent() {
            assertThatThrownBy(() -> new TaxpayerFacts(
                            DOB,
                            ResidentialStatus.RESIDENT,
                            Set.of(PersonType.INDIVIDUAL),
                            null,
                            Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Optional.empty()");
        }
    }

    @Nested
    @DisplayName("FamilySnapshot — the persons that per-person rules needed and did not have")
    class Family {

        @Test
        void theMemberListIsDefensivelyCopied() {
            List<Member> mutable = new ArrayList<>();
            mutable.add(child(LocalDate.of(2010, 1, 1)));
            FamilySnapshot snapshot = new FamilySnapshot(mutable);

            mutable.add(child(LocalDate.of(2012, 1, 1)));

            assertThat(snapshot.members()).hasSize(1);
        }

        @Test
        void relationshipSelectsTheSeparateCeilings() {
            // DED-80D-001 versus -002: self/family and parents are separate Rs 50,000 ceilings.
            Member parent = new Member(
                    Relationship.PARENT,
                    LocalDate.of(1950, 3, 3),
                    true,
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    false);
            FamilySnapshot snapshot =
                    new FamilySnapshot(List.of(child(LocalDate.of(2010, 1, 1)), parent));

            assertThat(snapshot.withRelationship(Relationship.PARENT)).containsExactly(parent);
            assertThat(snapshot.withRelationship(Relationship.SPOUSE)).isEmpty();
        }

        @Test
        void theTwoLtaExceptionsAreFactsAndNotDerivations() {
            // SAL-LTA-CHILD-001's exceptions. Neither is derivable from a date of birth: the
            // second is a fact about a birth, not about a person.
            Member twin = new Member(
                    Relationship.CHILD,
                    LocalDate.of(2001, 5, 5),
                    true,
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    true);

            assertThat(twin.multipleBirthAfterFirstChild()).isTrue();
            assertThat(twin.bornBeforeOct1998()).isFalse();
        }

        @Test
        void noFamilyIsAnEmptyListRatherThanNull() {
            assertThat(FamilySnapshot.none().members()).isEmpty();
            assertThatThrownBy(() -> new FamilySnapshot(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nobody asked");
        }

        @Test
        void memberOrderIsPreserved() {
            Member first = child(LocalDate.of(2010, 1, 1));
            Member second = child(LocalDate.of(2012, 1, 1));

            assertThat(new FamilySnapshot(List.of(first, second)).members())
                    .containsExactly(first, second);
        }

        @Test
        void aMemberWithoutARelationshipIsRejected() {
            assertThatThrownBy(() -> new Member(
                            null,
                            LocalDate.of(2010, 1, 1),
                            true,
                            Optional.empty(),
                            Optional.empty(),
                            false,
                            false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("relationship");
        }
    }

    @Nested
    @DisplayName("LedgerSnapshot — what a single year cannot hold")
    class Ledgers {

        @Test
        void theCarryForwardMapIsDefensivelyCopied() {
            Map<TaxYearId, Money> mutable = new HashMap<>();
            mutable.put(TaxYearId.of(2024), Money.of("200000"));
            LedgerSnapshot ledgers = new LedgerSnapshot(
                    0,
                    false,
                    Optional.empty(),
                    Money.ZERO,
                    Money.ZERO,
                    0,
                    mutable,
                    Optional.empty());

            mutable.put(TaxYearId.of(2024), Money.of("999999"));

            assertThat(ledgers.housePropertyLossFrom(TaxYearId.of(2024)))
                    .isEqualTo(Money.of("200000"));
        }

        @Test
        void everyFigureIsWhatWasConsumedRatherThanWhatRemains() {
            // A remaining balance is a subtraction, and the subtraction is the engine's job. This
            // is also why M3.4 wants append-only ledgers: a decremented counter cannot reproduce
            // a past year's answer.
            LedgerSnapshot ledgers = new LedgerSnapshot(
                    2,
                    true,
                    Optional.of(TaxYearId.of(2020)),
                    Money.of("500000"),
                    Money.of("300000"),
                    3,
                    Map.of(),
                    Optional.of(TaxYearId.of(2022)));

            assertThat(ledgers.gratuityAlreadyAllowed()).isEqualTo(Money.of("500000"));
            assertThat(ledgers.preConstructionInstalmentsClaimed()).isEqualTo(3);
            assertThat(ledgers.ltaJourneysClaimedInBlock()).isEqualTo(2);
        }

        @Test
        void vrsIsOnceInALifetimeAndTheLedgerSaysWhether() {
            assertThat(LedgerSnapshot.empty().vrsAlreadyClaimed()).isFalse();

            LedgerSnapshot claimed = new LedgerSnapshot(
                    0,
                    false,
                    Optional.of(TaxYearId.of(2019)),
                    Money.ZERO,
                    Money.ZERO,
                    0,
                    Map.of(),
                    Optional.empty());

            assertThat(claimed.vrsAlreadyClaimed()).isTrue();
        }

        @Test
        void anUnclaimedYearIsZeroRatherThanAbsent() {
            assertThat(LedgerSnapshot.empty().housePropertyLossFrom(TaxYearId.of(2024)))
                    .isEqualTo(Money.ZERO);
        }

        @Test
        void aNullAggregateIsRejectedBecauseNothingYetDiffersFromNeverAsked() {
            assertThatThrownBy(() -> new LedgerSnapshot(
                            0,
                            false,
                            Optional.empty(),
                            null,
                            Money.ZERO,
                            0,
                            Map.of(),
                            Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different ceilings");
        }

        @Test
        void aNegativeCountIsRejected() {
            assertThatThrownBy(() -> new LedgerSnapshot(
                            -1,
                            false,
                            Optional.empty(),
                            Money.ZERO,
                            Money.ZERO,
                            0,
                            Map.of(),
                            Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        void anEmptyLedgerIsAFirstReturn() {
            LedgerSnapshot empty = LedgerSnapshot.empty();

            assertThat(empty.gratuityAlreadyAllowed()).isEqualTo(Money.ZERO);
            assertThat(empty.housePropertyLossCarriedForward()).isEmpty();
            assertThat(empty.ltaCarryOverAvailable()).isFalse();
        }
    }
}
