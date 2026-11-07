package com.thiru.wealthlens.taxengine.rules.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierTest {

    @Nested
    @DisplayName("RuleId")
    class RuleIds {

        /**
         * The regression test for the erratum at {@code 02-TECH-SPEC.md} T3. The specified pattern
         * allowed at most three hyphenated segments, which rejects every one of these — including
         * the old-regime slab ladders and the rule recorded as STATUS UNCLEAR. Copying the spec
         * verbatim would have made them unconstructable.
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "RATE-SLAB-OLD-2025-A", "RATE-SLAB-OLD-2025-B", "RATE-SLAB-OLD-2025-C",
                "RATE-SLAB-OLD-2026-A", "RATE-SLAB-OLD-2026-B",
                "SAL-ALW-TRANSPORT-DIS-2026", "SAL-ALW-DUTY-LIST-2026",
                "SAL-ALW-NEW-SUBSET-001", "DED-80C-NPS-CAP-001", "SAL-DEF-80C-NPS-001"})
        void constructor_whenIdHasFourSegments_isAccepted(String id) {
            assertThat(new RuleId(id).value()).isEqualTo(id);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "SAL-HRA-001", "RATE-SLAB-NEW-TAX-REGIME", "REBATE-NEW-MR-001",
                "DED-80D-007", "PF-AGGCAP-001", "HP-CAP-SELFOCC-003"})
        void constructor_whenIdIsWellFormed_isAccepted(String id) {
            assertThat(new RuleId(id).value()).isEqualTo(id);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "sal-hra-001",          // lower case
                "SAL_HRA_001",          // underscores instead of hyphens
                "S-001",                // head shorter than two characters
                "SALHRA001",            // no segments at all
                "SAL-",                 // empty segment
                "SAL-HRA-001-A-B-C-D",  // more segments than the shape allows
                ""})
        void constructor_whenIdIsMalformed_isRejected(String id) {
            assertThatThrownBy(() -> new RuleId(id))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RuleId must be");
        }

        @Test
        void constructor_whenNull_isRejected() {
            assertThatThrownBy(() -> new RuleId(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("may not be null");
        }

        @Test
        void message_whenRejecting_saysWhatShapeWasExpected() {
            // A regex tells the reader what was rejected but not what to write instead.
            assertThatThrownBy(() -> new RuleId("nope"))
                    .hasMessageContaining("SAL-HRA-001");
        }
    }

    @Nested
    @DisplayName("NodeId")
    class NodeIds {

        @ParameterizedTest
        @ValueSource(strings = {
                "salary.gross", "tax.bybasis", "round.totalincome",
                "interest.and.fees", "employer.contrib.charges", "gti", "setoff"})
        void constructor_whenNodeNameIsWellFormed_isAccepted(String id) {
            assertThat(new NodeId(id).value()).isEqualTo(id);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Salary.Gross", "salary_gross", "ab", "1salary"})
        void constructor_whenNodeNameIsMalformed_isRejected(String id) {
            assertThatThrownBy(() -> new NodeId(id)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ComponentCode and RegimeCode")
    class Codes {

        @ParameterizedTest
        @ValueSource(strings = {
                "BASIC", "HRA", "DEARNESS_ALLOWANCE", "EMPLOYER_PF",
                "PERQ_EMPLOYER_CONTRIB_ACCRETION", "PERQ_RPF_EXCESS_12PC"})
        void componentCode_whenWellFormed_isAccepted(String code) {
            assertThat(new ComponentCode(code).value()).isEqualTo(code);
        }

        @ParameterizedTest
        @ValueSource(strings = {"basic", "EMPLOR-PF", "E", "_BASIC"})
        void componentCode_whenMalformed_isRejected(String code) {
            assertThatThrownBy(() -> new ComponentCode(code))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"NEW_TAX_REGIME", "FIRST_SCHEDULE_RATES", "S200", "S204", "SYN_FLAT"})
        void regimeCode_whenWellFormed_isAccepted(String code) {
            assertThat(new RegimeCode(code).value()).isEqualTo(code);
        }

        @Test
        void regimeCode_whenLongerThanThirtyTwo_isRejected() {
            assertThatThrownBy(() -> new RegimeCode("S" + "X".repeat(32)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ItrFormCode and ScheduleCode")
    class FormCodes {

        @ParameterizedTest
        @ValueSource(strings = {"ITR-1", "ITR-7", "ITR-V", "ITR-UN", "SAHAJ", "SUGAM"})
        void itrFormCode_whenWellFormed_isAccepted(String code) {
            assertThat(new ItrFormCode(code).value()).isEqualTo(code);
        }

        @ParameterizedTest
        @ValueSource(strings = {"itr-1", "IT", "ITR_1"})
        void itrFormCode_whenMalformed_isRejected(String code) {
            assertThatThrownBy(() -> new ItrFormCode(code))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"SCH-S", "SCH-HP", "SCH-VIA", "SCH-TDS1", "SCH-IT"})
        void scheduleCode_whenWellFormed_isAccepted(String code) {
            assertThat(new ScheduleCode(code).value()).isEqualTo(code);
        }
    }

    @Nested
    @DisplayName("TaxYearId")
    class TaxYears {

        @Test
        void taxYear2026_hasTheExpectedLabelAndBoundaries() {
            TaxYearId ty = TaxYearId.of(2026);

            assertThat(ty.label()).isEqualTo("2026-27");
            assertThat(ty.startsOn()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(ty.endsOn()).isEqualTo(LocalDate.of(2027, 3, 31));
        }

        @Test
        void taxYear2025_hasTheExpectedLabelAndBoundaries() {
            TaxYearId ty = TaxYearId.of(2025);

            assertThat(ty.label()).isEqualTo("2025-26");
            assertThat(ty.startsOn()).isEqualTo(LocalDate.of(2025, 4, 1));
            assertThat(ty.endsOn()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        /**
         * The regression test for the second erratum at {@code 02-TECH-SPEC.md} T3. Without
         * zero-padding this renders "2099-0", because 2100 % 100 is 0. The synthetic pack used to
         * prove the architecture's extensibility is TY 2099-00.
         */
        @Test
        void label_whenTheYearCrossesACentury_isZeroPadded() {
            assertThat(TaxYearId.of(2099).label()).isEqualTo("2099-00");
            assertThat(TaxYearId.of(2100).label()).isEqualTo("2100-01");
        }

        @Test
        void containing_whenTheDateIsTheFirstOfApril_isTheYearThatStarts() {
            // 1 April 2026 begins TY 2026-27
            assertThat(TaxYearId.containing(LocalDate.of(2026, 4, 1))).isEqualTo(TaxYearId.of(2026));
        }

        @Test
        void containing_whenTheDateIsTheThirtyFirstOfMarch_isTheYearThatEnds() {
            // 31 March 2027 ends TY 2026-27
            assertThat(TaxYearId.containing(LocalDate.of(2027, 3, 31))).isEqualTo(TaxYearId.of(2026));
        }

        @Test
        void containing_whenTheDateIsTheDayBeforeAYearStarts_isTheEarlierYear() {
            // 31 March 2026 is the last day of TY 2025-26
            assertThat(TaxYearId.containing(LocalDate.of(2026, 3, 31))).isEqualTo(TaxYearId.of(2025));
        }

        @Test
        void containing_whenTheDateIsInJanuary_isTheYearThatBeganTheAprilBefore() {
            // 15 January 2027 is after 1 April 2026 and before 1 April 2027
            assertThat(TaxYearId.containing(LocalDate.of(2027, 1, 15))).isEqualTo(TaxYearId.of(2026));
        }

        @Test
        void containing_whenNull_isRefused() {
            assertThatThrownBy(() -> TaxYearId.containing(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("date");
        }

        @Test
        void contains_whenDateIsOnEitherBoundary_isTrue() {
            TaxYearId ty = TaxYearId.of(2026);

            assertThat(ty.contains(LocalDate.of(2026, 4, 1))).isTrue();
            assertThat(ty.contains(LocalDate.of(2027, 3, 31))).isTrue();
        }

        @Test
        void contains_whenDateIsOutside_isFalse() {
            TaxYearId ty = TaxYearId.of(2026);

            assertThat(ty.contains(LocalDate.of(2026, 3, 31))).isFalse();
            assertThat(ty.contains(LocalDate.of(2027, 4, 1))).isFalse();
        }

        @Test
        void taxYears_areValueEqual() {
            assertThat(TaxYearId.of(2026)).isEqualTo(new TaxYearId(2026));
            assertThat(TaxYearId.of(2026)).isNotEqualTo(TaxYearId.of(2025));
        }

        @Test
        void aLeapYearBoundary_isStillTheLastOfMarch() {
            // 2028 is a leap year; the tax year still ends 31 March, not 29 February.
            assertThatCode(() -> assertThat(TaxYearId.of(2027).endsOn())
                    .isEqualTo(LocalDate.of(2028, 3, 31))).doesNotThrowAnyException();
        }
    }
}
