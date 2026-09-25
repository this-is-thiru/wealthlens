package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Member;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot.Relationship;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The facts the engine is given about an assessee, and the history behind them. Formerly
 * {@code TaxpayerFactsTest}; the assessee family replaced {@code TaxpayerFacts} (D-039, D-062).
 */
class AssesseeTest {

    private static final LocalDate DOB = LocalDate.of(1966, 11, 20);

    private static Individual individual() {
        return new Individual(DOB, ResidentialStatus.RESIDENT, Optional.empty(), Optional.empty());
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
    @DisplayName("Assessee — one record per type, each carrying only the facts that type has (D-039)")
    class Assessees {

        @Test
        void permittedSubclasses_whenTheFamilyIsListed_areExactlyTheTypesBuilt() {
            // When
            List<String> members = Arrays.stream(Assessee.class.getPermittedSubclasses())
                    .map(Class::getSimpleName)
                    .toList();

            // Then — Firm, Company and the rest join as they are built
            assertThat(members).containsExactlyInAnyOrder("Individual", "Huf");
        }

        @Test
        void type_whenAsked_isFixedByTheRecordRatherThanStored() {
            // When / Then — a person has one type; the old Set<PersonType> was a modelling error
            assertThat(individual().type()).isEqualTo(PersonType.INDIVIDUAL);
            assertThat(new Huf(ResidentialStatus.RESIDENT).type()).isEqualTo(PersonType.HUF);
        }

        @Test
        void recordComponents_whenTheAssesseeIsAnHuf_carryOnlyResidence() {
            // When / Then — an HUF has no date of birth and no disability; karta and members
            // arrive with the first rule that reads them (brief rule B2)
            assertThat(Huf.class.getRecordComponents())
                    .extracting(RecordComponent::getName)
                    .containsExactly("residence");
        }

        @Test
        void recordComponents_whenTheAssesseeIsAnIndividual_carryADateOfBirthAndNoPanOrAge() {
            // When / Then — D-025: a date of birth, because F2's senior-citizen test is "at any
            // time during the previous year", which an age cannot answer. No PAN: no rule
            // computes on one and O1.1 wants it masked wherever it is logged.
            assertThat(Individual.class.getRecordComponents())
                    .extracting(RecordComponent::getName)
                    .containsExactly("dateOfBirth", "residence", "disability", "dateOfDeath");
        }

        @Test
        void residentialStatus_whenListed_hasThreeStatesRatherThanTwo() {
            // When / Then — ConditionContext collapses these to boolean resident, which is lossy:
            // RNOR is resident for some provisions and not others. The projection keeps what was known.
            assertThat(ResidentialStatus.values())
                    .containsExactly(
                            ResidentialStatus.RESIDENT,
                            ResidentialStatus.RNOR,
                            ResidentialStatus.NON_RESIDENT);
        }

        @Test
        void disability_whenCertified_isTheSeverityAndNotTheCertificate() {
            // Given — DED-80U-001 and DED-80DD-001 read the severity; nothing reads the certificate
            Individual individual = new Individual(
                    DOB, ResidentialStatus.RESIDENT, Optional.of(DisabilitySeverity.SEVERE_DISABILITY), Optional.empty());

            // When / Then
            assertThat(individual.disability()).contains(DisabilitySeverity.SEVERE_DISABILITY);
        }

        @Test
        void individual_whenDeathPrecedesBirth_isRejected() {
            // When / Then
            assertThatThrownBy(() -> new Individual(
                            DOB, ResidentialStatus.RESIDENT, Optional.empty(), Optional.of(DOB.minusDays(1))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("precedes");
        }

        @Test
        void individual_whenAnOptionalIsNull_isRejectedRatherThanTreatedAsAbsent() {
            // When / Then
            assertThatThrownBy(() -> new Individual(DOB, ResidentialStatus.RESIDENT, null, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Optional.empty()");
        }

        @Test
        void individual_whenNoDateOfBirth_isRejected() {
            // When / Then
            assertThatThrownBy(() -> new Individual(null, ResidentialStatus.RESIDENT, Optional.empty(), Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("date of birth");
        }

        @Test
        void constructors_whenNoResidence_areRejected() {
            // When / Then
            assertThatThrownBy(() -> new Individual(DOB, null, Optional.empty(), Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("residential status");
            assertThatThrownBy(() -> new Huf(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("residential status");
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
