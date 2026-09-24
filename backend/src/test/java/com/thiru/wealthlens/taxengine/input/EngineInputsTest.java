package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.TaxpayerFacts.ResidentialStatus;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Everything the engine may read, in one object — which is what makes it pure.
 */
class EngineInputsTest {

    private static final TaxYearId TY = TaxYearId.of(2026);
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 21);

    private static EngineInputs complete() {
        return new EngineInputs(
                TY,
                new RegimeCode("NEW_TAX_REGIME"),
                TemporalContext.forTaxYear(TY, AS_OF),
                EnteredFacts.none(),
                new TaxpayerFacts(
                        LocalDate.of(1980, 5, 5),
                        ResidentialStatus.RESIDENT,
                        Set.of(PersonType.INDIVIDUAL),
                        Optional.empty(),
                        Optional.empty()),
                FamilySnapshot.none(),
                LedgerSnapshot.empty(),
                RegimeElectionSnapshot.none(),
                CapitalGainsInput.none(Instant.parse("2026-09-01T00:00:00Z"), "pf-run-1"),
                FilingFacts.notYetFurnished(LocalDate.of(2026, 7, 31)));
    }

    @Nested
    @DisplayName("the record is complete enough that the engine fetches nothing")
    class Completeness {

        @Test
        void everyComponentIsMandatory() {
            // W-038's fingerprint is the cache key and is derived from this record. A component
            // left out is an input the key cannot see, so two computations differing only in it
            // would collide and one would be served the other's answer.
            EngineInputs complete = complete();

            for (RecordComponent component : EngineInputs.class.getRecordComponents()) {
                assertThat(valueOf(complete, component))
                        .as("%s must be populated in a complete EngineInputs", component.getName())
                        .isNotNull();
            }
        }

        @Test
        void aMissingComponentIsRejectedAndSaysWhy() {
            assertThatThrownBy(() -> new EngineInputs(
                            TY,
                            new RegimeCode("NEW_TAX_REGIME"),
                            TemporalContext.forTaxYear(TY, AS_OF),
                            null,
                            complete().taxpayer(),
                            FamilySnapshot.none(),
                            LedgerSnapshot.empty(),
                            RegimeElectionSnapshot.none(),
                            complete().capitalGains(),
                            complete().filing()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("entered")
                    .hasMessageContaining("the key cannot see");
        }

        @Test
        void theTenComponentsAreA3sList() {
            assertThat(EngineInputs.class.getRecordComponents())
                    .extracting(RecordComponent::getName)
                    .containsExactly(
                            "taxYear",
                            "regime",
                            "temporal",
                            "entered",
                            "taxpayer",
                            "family",
                            "ledgers",
                            "elections",
                            "capitalGains",
                            "filing");
        }

        @Test
        void thereIsNoFingerprintMethodYet() {
            // A3 shows one. It returns InputsFingerprint, which W-038 owns and builds next.
            assertThat(Arrays.stream(EngineInputs.class.getDeclaredMethods())
                            .map(java.lang.reflect.Method::getName))
                    .doesNotContain("fingerprint");
        }
    }

    @Nested
    @DisplayName("nothing here comes from a module the engine may not read")
    class ModuleBoundary {

        /**
         * B-003 turned into a regression guard.
         *
         * <p>A3's own component list named {@code DraftInputs} ({@code taxplanning}) and
         * {@code CapitalGainsSummary} ({@code portfolio}), neither of which {@code taxengine} may
         * depend on — and A3 recommends the ArchUnit rule that forbids the first, three code blocks
         * below the record that broke it. The modulith test does catch this, but two layers away
         * and with a message about packages. This fails here, immediately, naming the component.
         */
        @Test
        void everyComponentTypeIsOneTheEngineMayDependOn() {
            for (RecordComponent component : EngineInputs.class.getRecordComponents()) {
                String packageName = component.getType().getPackageName();

                assertThat(packageName)
                        .as(
                                "EngineInputs.%s is a %s. taxengine may depend only on itself,"
                                        + " shared and the JDK — see B-003 and D-024: a component"
                                        + " from taxplanning or portfolio makes the engine impure"
                                        + " and cannot compile under the module rules",
                                component.getName(),
                                component.getType().getName())
                        .satisfiesAnyOf(
                                p -> assertThat(p)
                                        .startsWith("com.thiru.wealthlens.taxengine"),
                                p -> assertThat(p).startsWith("com.thiru.wealthlens.shared"),
                                p -> assertThat(p).startsWith("java."));
            }
        }

        @Test
        void theEnteredSideIsAProjectionAndNotTheDraft() {
            assertThat(EngineInputs.class.getRecordComponents())
                    .extracting(c -> c.getType().getSimpleName())
                    .doesNotContain("DraftInputs", "CapitalGainsSummary", "TaxpayerProfile");
        }
    }

    @Nested
    @DisplayName("the year is stated twice and the two must agree")
    class Consistency {

        @Test
        void aTemporalContextForAnotherYearIsRejected() {
            // Rules keyed on EngineInputs.taxYear and rules reading TemporalContext would other-
            // wise compute different years from one object, and nothing would say so.
            assertThatThrownBy(() -> new EngineInputs(
                            TY,
                            new RegimeCode("NEW_TAX_REGIME"),
                            TemporalContext.forTaxYear(TaxYearId.of(2025), AS_OF),
                            EnteredFacts.none(),
                            complete().taxpayer(),
                            FamilySnapshot.none(),
                            LedgerSnapshot.empty(),
                            RegimeElectionSnapshot.none(),
                            complete().capitalGains(),
                            complete().filing()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must agree");
        }

        @Test
        void theAsOfDateComesFromTheInputsRatherThanAClock() {
            assertThat(complete().asOfDate()).isEqualTo(AS_OF);
        }
    }

    private static Object valueOf(EngineInputs inputs, RecordComponent component) {
        try {
            return component.getAccessor().invoke(inputs);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not read " + component.getName(), e);
        }
    }
}
