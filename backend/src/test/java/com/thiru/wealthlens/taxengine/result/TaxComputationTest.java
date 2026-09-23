package com.thiru.wealthlens.taxengine.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.InputsFingerprint;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.registry.DraftOrFinal;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationStatus;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The split that lets one half be cached forever.
 */
class TaxComputationTest {

    private static final TaxYearId TY = TaxYearId.of(2026);
    private static final RuleId SLAB = new RuleId("RATE-SLAB-NEW-2026");
    private static final RuleId STD = new RuleId("SAL-STDDED-001");

    /** Every date-and-time type the JDK offers, so the structural check cannot be fooled. */
    private static final Set<Class<?>> DATE_TYPES = Set.of(
            LocalDate.class,
            LocalDateTime.class,
            Instant.class,
            ZonedDateTime.class,
            OffsetDateTime.class,
            Year.class,
            java.time.YearMonth.class,
            java.util.Date.class);

    private static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                "Income-tax Act 2025",
                DraftOrFinal.FINAL,
                LocalDate.of(2026, 9, 19),
                VerificationStatus.VERIFIED,
                null);
    }

    private static Map<RulePackLayer, RulePackRef> lawAndExec() {
        Map<RulePackLayer, RulePackRef> layers = new EnumMap<>(RulePackLayer.class);
        for (RulePackLayer layer : RulePackLayer.values()) {
            if (layer.affectsComputation()) {
                String name = layer.name().toLowerCase(java.util.Locale.ROOT);
                layers.put(
                        layer,
                        new RulePackRef(layer, "2026-27." + name + ".1", "sha256:" + "a".repeat(64)));
            }
        }
        return layers;
    }

    private static TraceLine line(RuleId rule) {
        return TraceLine.of(
                1,
                new NodeId("tax.bybasis"),
                rule,
                StepKind.TAX,
                MoneyValue.of(Money.ZERO),
                MoneyValue.of(Money.of("60000")),
                provenance());
    }

    private static TaxComputation computation() {
        return new TaxComputation(
                ComputationId.fresh(),
                TY,
                new RegimeCode("S202_DEFAULT"),
                lawAndExec(),
                InputsFingerprint.of(NodeHarness.plainTaxpayer()),
                List.of(line(SLAB), line(STD)),
                Money.of("1200000"),
                TaxByBasis.atSlabRatesOnly(Money.of("60000")),
                Money.of("62400"));
    }

    @Nested
    @DisplayName("TaxComputation holds no date-dependent field")
    class Cacheable {

        @Test
        void noComponentIsADateOrATime() {
            // The property that makes this cacheable indefinitely, asserted structurally rather
            // than by inspection -- it is easy to break by adding one obviously-useful field.
            for (RecordComponent component : TaxComputation.class.getRecordComponents()) {
                assertThat(DATE_TYPES)
                        .as(
                                "TaxComputation.%s is a %s. A6 splits the answer so that this half"
                                        + " never moves because a day passed; a date here would make"
                                        + " it uncacheable and belongs in FilingPosition",
                                component.getName(), component.getType().getSimpleName())
                        .doesNotContain(component.getType());
            }
        }

        @Test
        void theComponentsAreA6sList() {
            assertThat(TaxComputation.class.getRecordComponents())
                    .extracting(RecordComponent::getName)
                    .containsExactly(
                            "id",
                            "taxYear",
                            "regime",
                            "lawAndExec",
                            "inputs",
                            "trace",
                            "totalIncome",
                            "taxByBasis",
                            "netPayableBeforeFilingEffects");
        }

        @Test
        void theCacheKeyHalvesAreBothPresent() {
            TaxComputation computation = computation();

            assertThat(computation.inputs().sha256()).hasSize(64);
            assertThat(computation.lawAndExec()).isNotEmpty();
        }

        @Test
        void anEditorialLayerIsRefusedBecauseItWouldInvalidateEverything() {
            // G-11: content and forms are excluded so that seven catalogue revisions invalidate
            // not one cached figure. Admitting one here would silently undo that.
            Map<RulePackLayer, RulePackRef> withContent = new EnumMap<>(lawAndExec());
            withContent.put(
                    RulePackLayer.CONTENT,
                    new RulePackRef(
                            RulePackLayer.CONTENT, "2026-27.content.1", "sha256:" + "b".repeat(64)));

            assertThatThrownBy(() -> new TaxComputation(
                            ComputationId.fresh(),
                            TY,
                            new RegimeCode("S202_DEFAULT"),
                            withContent,
                            InputsFingerprint.of(NodeHarness.plainTaxpayer()),
                            List.of(),
                            Money.ZERO,
                            TaxByBasis.atSlabRatesOnly(Money.ZERO),
                            Money.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("G-11");
        }

        @Test
        void anAnswerWithNoPackVersionsCannotBeReproducedAndIsRefused() {
            assertThatThrownBy(() -> new TaxComputation(
                            ComputationId.fresh(),
                            TY,
                            new RegimeCode("S202_DEFAULT"),
                            Map.of(),
                            InputsFingerprint.of(NodeHarness.plainTaxpayer()),
                            List.of(),
                            Money.ZERO,
                            TaxByBasis.atSlabRatesOnly(Money.ZERO),
                            Money.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be reproduced");
        }

        @Test
        void theTraceTravelsWithTheAnswerAndIsAddressableByRule() {
            // M5.1: every figure is addressable by rule id, never by index or field name.
            TaxComputation computation = computation();

            assertThat(computation.linesFor(SLAB)).hasSize(1);
            assertThat(computation.linesFor(new RuleId("SAL-HRA-001"))).isEmpty();
        }

        @Test
        void theTraceAndPackRefsAreDefensivelyCopied() {
            List<TraceLine> mutable = new ArrayList<>();
            mutable.add(line(SLAB));
            TaxComputation computation = new TaxComputation(
                    ComputationId.fresh(),
                    TY,
                    new RegimeCode("S202_DEFAULT"),
                    lawAndExec(),
                    InputsFingerprint.of(NodeHarness.plainTaxpayer()),
                    mutable,
                    Money.ZERO,
                    TaxByBasis.atSlabRatesOnly(Money.ZERO),
                    Money.ZERO);

            mutable.add(line(STD));

            assertThat(computation.trace()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("FilingPosition holds the date-dependent ones")
    class DateDependent {

        @Test
        void asOfIsCarriedRatherThanRead() {
            // The engine reads no clock: a clock inside makes a golden test time-dependent and an
            // audit irreproducible.
            FilingPosition position = new FilingPosition(
                    ComputationId.fresh(), LocalDate.of(2026, 12, 25), Money.of("70000"));

            assertThat(position.asOf()).isEqualTo(LocalDate.of(2026, 12, 25));
        }

        @Test
        void itCarriesADateWhereTaxComputationCarriesNone() {
            // The split, stated as one assertion.
            assertThat(FilingPosition.class.getRecordComponents())
                    .extracting(RecordComponent::getType)
                    .contains(LocalDate.class);

            assertThat(TaxComputation.class.getRecordComponents())
                    .extracting(RecordComponent::getType)
                    .doesNotContain(LocalDate.class);
        }

        @Test
        void itNamesTheComputationItOverlays() {
            ComputationId id = ComputationId.fresh();

            assertThat(new FilingPosition(id, LocalDate.of(2026, 7, 31), Money.ZERO).basedOn())
                    .isEqualTo(id);
        }

        @Test
        void anAbsentAsOfIsRefusedRatherThanDefaultedToToday() {
            assertThatThrownBy(() -> new FilingPosition(ComputationId.fresh(), null, Money.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("irreproducible");
        }

        @Test
        void aPositionWithNoComputationBehindItIsRefused() {
            assertThatThrownBy(() -> new FilingPosition(null, LocalDate.of(2026, 7, 31), Money.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be explained or reproduced");
        }
    }

    @Nested
    @DisplayName("ComputationId and its receipt")
    class Identity {

        @Test
        void aFreshIdIsAUuid() {
            assertThat(ComputationId.fresh().value())
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        void twoFreshIdsDiffer() {
            assertThat(ComputationId.fresh()).isNotEqualTo(ComputationId.fresh());
        }

        @Test
        void anIdThatIsNotAUuidIsRefused() {
            assertThatThrownBy(() -> new ComputationId("TXC-2026-9F3A21"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lower-case UUID");
        }

        @Test
        void theReceiptReadsAsE6Describes() {
            ComputationReceipt receipt = ComputationReceipt.of(ComputationId.fresh(), TY);

            assertThat(receipt.value()).matches("TXC-2026-[A-Z2-7]{6}");
        }

        @Test
        void theSameIdAlwaysPrintsTheSameReceipt() {
            // Derived, not generated. The PDF and the error body must agree, and a receipt that
            // drifted from its computation would be worse than none.
            ComputationId id = ComputationId.fresh();

            assertThat(ComputationReceipt.of(id, TY)).isEqualTo(ComputationReceipt.of(id, TY));
        }

        @Test
        void differentIdsGiveDifferentReceipts() {
            assertThat(ComputationReceipt.of(ComputationId.fresh(), TY))
                    .isNotEqualTo(ComputationReceipt.of(ComputationId.fresh(), TY));
        }

        @Test
        void theReceiptOmitsTheCharactersThatAreMisreadAloud() {
            // RFC 4648 base32 has no 0, 1, 8 or 9 -- it is read out in support conversations.
            for (int attempt = 0; attempt < 50; attempt++) {
                String suffix =
                        ComputationReceipt.of(ComputationId.fresh(), TY).value().substring(9);

                assertThat(suffix).doesNotContain("0").doesNotContain("1").doesNotContain("8");
            }
        }

        @Test
        void aMalformedReceiptIsRefused() {
            assertThatThrownBy(() -> new ComputationReceipt("TXC-2026-999999"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TXC-<year>");
        }

        @Test
        void aComputationCanPrintItsOwnReceipt() {
            TaxComputation computation = computation();

            assertThat(computation.receipt())
                    .isEqualTo(ComputationReceipt.of(computation.id(), TY));
        }
    }
}
