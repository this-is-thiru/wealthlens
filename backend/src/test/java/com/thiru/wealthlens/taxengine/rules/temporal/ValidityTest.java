package com.thiru.wealthlens.taxengine.rules.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.registry.DraftOrFinal;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationStatus;
import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValidityTest {

    private static final LocalDate APR_2026 = LocalDate.of(2026, 4, 1);
    private static final LocalDate MAR_2027 = LocalDate.of(2027, 3, 31);

    @Nested
    @DisplayName("Between")
    class BetweenTests {

        @Test
        void contains_whenDateIsOnEitherBoundary_isTrue() {
            Validity validity = new Validity.Between(APR_2026, Optional.of(MAR_2027));

            assertThat(validity.contains(APR_2026)).isTrue();
            assertThat(validity.contains(MAR_2027)).isTrue();
        }

        @Test
        void contains_whenDateIsOutside_isFalse() {
            Validity validity = new Validity.Between(APR_2026, Optional.of(MAR_2027));

            assertThat(validity.contains(APR_2026.minusDays(1))).isFalse();
            assertThat(validity.contains(MAR_2027.plusDays(1))).isFalse();
        }

        @Test
        void contains_whenOpenEnded_hasNoUpperBound() {
            Validity validity = Validity.Between.fromOnwards(APR_2026);

            assertThat(validity.contains(LocalDate.of(2999, 1, 1))).isTrue();
            assertThat(validity.contains(APR_2026.minusDays(1))).isFalse();
        }
    }

    @Nested
    @DisplayName("Retrospective")
    class RetrospectiveTests {

        /**
         * The Finance Act 2026 inserted a provision deemed effective from 1 June 2007. Both dates
         * are kept: the deemed date decides what the law is, the enactment date explains why a
         * computation made before it said something else.
         */
        @Test
        void contains_usesTheDeemedDate_notTheEnactmentDate() {
            LocalDate deemedFrom = LocalDate.of(2007, 6, 1);
            LocalDate enactedOn = LocalDate.of(2026, 3, 30);
            Validity validity = new Validity.Retrospective(deemedFrom, Optional.empty(), enactedOn);

            assertThat(validity.contains(LocalDate.of(2008, 1, 1))).isTrue();
            assertThat(validity.contains(deemedFrom.minusDays(1))).isFalse();
        }

        @Test
        void bothDates_areRetained() {
            LocalDate deemedFrom = LocalDate.of(2007, 6, 1);
            LocalDate enactedOn = LocalDate.of(2026, 3, 30);
            Validity.Retrospective validity =
                    new Validity.Retrospective(deemedFrom, Optional.empty(), enactedOn);

            assertThat(validity.deemedEffectiveFrom()).isEqualTo(deemedFrom);
            assertThat(validity.actuallyEnactedOn()).isEqualTo(enactedOn);
        }

        @Test
        void construction_whenNotActuallyRetrospective_isRejected() {
            assertThatThrownBy(() -> new Validity.Retrospective(
                    LocalDate.of(2026, 4, 1), Optional.empty(), LocalDate.of(2025, 1, 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Use Validity.Between instead");
        }
    }

    @Nested
    @DisplayName("PendingNotification")
    class PendingNotificationTests {

        private static final Provenance BASIS = new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2026/271439.pdf"),
                "Finance Act 2026 s.123, Schedule III Note 12",
                DraftOrFinal.FINAL,
                LocalDate.of(2026, 9, 18),
                VerificationStatus.VERIFIED,
                null);

        /**
         * The rule is in the statute and is not in force. It never applies on any date; the interim
         * rule does. Returning true for some date would silently apply a rule that has not commenced.
         */
        @Test
        void contains_isAlwaysFalse_whateverTheDate() {
            Validity validity = new Validity.PendingNotification(
                    new RuleId("SAL-DISPENSION-INTERIM-001"),
                    "pending notification, the entire disability pension is exempt",
                    BASIS);

            assertThat(validity.contains(LocalDate.of(1900, 1, 1))).isFalse();
            assertThat(validity.contains(LocalDate.now())).isFalse();
            assertThat(validity.contains(LocalDate.of(2999, 12, 31))).isFalse();
        }

        @Test
        void construction_whenNoInterimRuleIsNamed_isRejected() {
            assertThatThrownBy(() -> new Validity.PendingNotification(null, "something", BASIS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("I16");
        }

        @Test
        void construction_whenTheInterimPositionIsNotDescribed_isRejected() {
            assertThatThrownBy(() -> new Validity.PendingNotification(
                    new RuleId("SAL-DISPENSION-INTERIM-001"), "  ", BASIS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("what applies instead");
        }
    }

    @Nested
    @DisplayName("DateRange")
    class DateRanges {

        @Test
        void coversTaxYear_whenTheRangeSpansIt_isTrue() {
            assertThat(DateRange.fromOnwards(APR_2026).coversTaxYear(TaxYearId.of(2026))).isTrue();
            assertThat(DateRange.between(APR_2026, MAR_2027).coversTaxYear(TaxYearId.of(2026))).isTrue();
        }

        @Test
        void coversTaxYear_whenTheRangeEndsMidYear_isFalse() {
            DateRange closesMidYear = DateRange.between(APR_2026, LocalDate.of(2026, 10, 1));

            assertThat(closesMidYear.coversTaxYear(TaxYearId.of(2026))).isFalse();
            assertThat(closesMidYear.overlapsTaxYear(TaxYearId.of(2026))).isTrue();
        }

        /**
         * The grandfathering shape: a regime closed to new entrants in 2024 but still in force.
         * The two windows are separate intervals, and the entry window must lie within the
         * in-force window (invariant I10).
         */
        @Test
        void liesWithin_whenAnEntryWindowClosedEarly_isTrue() {
            DateRange inForce = DateRange.fromOnwards(LocalDate.of(2019, 10, 1));
            DateRange entry = DateRange.between(LocalDate.of(2019, 10, 1), LocalDate.of(2024, 3, 31));

            assertThat(entry.liesWithin(inForce)).isTrue();
            assertThat(entry.contains(LocalDate.of(2026, 4, 1))).isFalse();
            assertThat(inForce.contains(LocalDate.of(2026, 4, 1))).isTrue();
        }

        @Test
        void liesWithin_whenTheEntryWindowOutlastsTheRegime_isFalse() {
            DateRange inForce = DateRange.between(LocalDate.of(2019, 10, 1), LocalDate.of(2024, 3, 31));
            DateRange entry = DateRange.fromOnwards(LocalDate.of(2019, 10, 1));

            assertThat(entry.liesWithin(inForce)).isFalse();
        }

        @Test
        void overlaps_whenRangesTouchAtOneDay_isTrue() {
            DateRange first = DateRange.between(APR_2026, LocalDate.of(2026, 10, 1));
            DateRange second = DateRange.between(LocalDate.of(2026, 10, 1), MAR_2027);

            assertThat(first.overlaps(second)).isTrue();
        }

        @Test
        void overlaps_whenRangesAreDisjoint_isFalse() {
            DateRange first = DateRange.between(APR_2026, LocalDate.of(2026, 9, 30));
            DateRange second = DateRange.between(LocalDate.of(2026, 10, 1), MAR_2027);

            assertThat(first.overlaps(second)).isFalse();
        }

        @Test
        void construction_whenTheRangeEndsBeforeItBegins_isRejected() {
            assertThatThrownBy(() -> DateRange.between(MAR_2027, APR_2026))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ends before it begins");
        }
    }
}
