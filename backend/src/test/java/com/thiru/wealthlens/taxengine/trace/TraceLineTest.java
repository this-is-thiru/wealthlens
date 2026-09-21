package com.thiru.wealthlens.taxengine.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxrules.id.NodeId;
import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.money.Money;
import com.thiru.wealthlens.taxrules.registry.DraftOrFinal;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import com.thiru.wealthlens.taxrules.registry.VerificationStatus;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The computation, and the layer that is only ever talk about it.
 */
class TraceLineTest {

    private static final NodeId NODE = new NodeId("salary.hra");
    private static final RuleId HRA = new RuleId("SAL-HRA-001");
    private static final RuleId STD = new RuleId("SAL-STDDED-001");

    private static Provenance verified() {
        return new Provenance(
                URI.create("https://example.invalid/act"),
                "Income-tax Act 2025, s.19",
                DraftOrFinal.FINAL,
                LocalDate.of(2026, 4, 1),
                VerificationStatus.VERIFIED,
                null);
    }

    private static NodeValue rupees(String amount) {
        return MoneyValue.of(Money.of(amount));
    }

    private static TraceLine hraLine() {
        return TraceLine.limited(
                7,
                NODE,
                HRA,
                StepKind.EXEMPTION,
                rupees("600000"),
                rupees("228000"),
                "rent less 10% of salary",
                verified());
    }

    @Nested
    @DisplayName("a trace line carries its rule id and provenance")
    class TheComputation {

        @Test
        void everyFigureIsAddressableByRuleId() {
            // M5.1: reports, the ITR mapping and ScenarioDiff all read by rule id, never by index
            assertThat(hraLine().rule()).isEqualTo(HRA);
            assertThat(hraLine().node()).isEqualTo(NODE);
        }

        @Test
        void theProvenanceTravelsWithTheFigure() {
            Provenance provenance = hraLine().provenance();

            assertThat(provenance.status()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(provenance.sourceDescription()).isEqualTo("Income-tax Act 2025, s.19");
        }

        @Test
        void aLineWithoutAProvenanceIsRejected() {
            assertThatThrownBy(() -> TraceLine.of(
                            1, NODE, HRA, StepKind.TAX, rupees("1"), rupees("1"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("provenance");
        }

        @Test
        void aLineWithoutARuleIsRejected() {
            assertThatThrownBy(() -> TraceLine.of(
                            1, NODE, null, StepKind.TAX, rupees("1"), rupees("1"), verified()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("addressable by rule id");
        }

        @Test
        void limitedByNamesWhatBoundTheFigure() {
            assertThat(hraLine().limitedBy()).contains("rent less 10% of salary");
        }

        @Test
        void nothingBoundIsEmptyRatherThanNull() {
            TraceLine line = TraceLine.of(
                    2, NODE, STD, StepKind.DEDUCTION, rupees("0"), rupees("75000"), verified());

            assertThat(line.limitedBy()).isEmpty();
        }

        @Test
        void aBlankReasonIsRejectedRatherThanRendered() {
            // "limited by nothing-in-particular" is a column of whitespace in G7.2's table
            assertThatThrownBy(() -> new TraceLine(
                            1,
                            NODE,
                            HRA,
                            StepKind.EXEMPTION,
                            rupees("1"),
                            rupees("1"),
                            Optional.of("  "),
                            verified()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void aMissingValueIsRejectedBecauseASilentZeroIsTheDefectBeingPrevented() {
            // G7.1: a step that produces nothing says so with a zero, which renders with a reason.
            // A null renders as a blank, which is O-1's silent zero.
            assertThatThrownBy(() -> TraceLine.of(
                            1, NODE, HRA, StepKind.EXEMPTION, rupees("1"), null, verified()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("silent zero");
        }

        @Test
        void aNegativeSequenceIsRejected() {
            assertThatThrownBy(() -> TraceLine.of(
                            -1, NODE, HRA, StepKind.TAX, rupees("1"), rupees("1"), verified()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }
    }

    @Nested
    @DisplayName("an annotation is not a notice")
    class TheLayerAbove {

        @Test
        void anAnnotationHoldsNoticesAndIsNotOne() {
            Notice notice = Notice.about(Severity.ATTENTION, "Rent receipts not on file", HRA);
            TraceAnnotation annotation = TraceAnnotation.of(7, notice);

            // The distinction the Verify names: one is the container keyed to a line, the other is
            // a thing said. An annotation with two notices is still one annotation.
            assertThat(annotation.notices()).containsExactly(notice);
            assertThat(annotation).isNotInstanceOf(Notice.class);
            assertThat(annotation.sequence()).isEqualTo(7);
        }

        @Test
        void anAnnotationIsBoundToItsLineOnlyBySequence() {
            TraceLine line = hraLine();
            TraceAnnotation annotation =
                    TraceAnnotation.of(line.sequence(), Notice.of(Severity.INFO, "Non-cash"));

            assertThat(annotation.sequence()).isEqualTo(line.sequence());
        }

        @Test
        void aLineCarriesNoCommentaryOfItsOwn() {
            // A8's whole point. If this ever compiles with a notices accessor on TraceLine, the
            // layering has been undone and I27 stops being checkable.
            assertThat(TraceLine.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .containsExactly(
                            "sequence", "node", "rule", "kind", "in", "out", "limitedBy",
                            "provenance");
        }

        @Test
        void nothingToSayIsAnEmptyAnnotationRatherThanNull() {
            TraceAnnotation annotation = new TraceAnnotation(3, List.of());

            assertThat(annotation.isEmpty()).isTrue();
            assertThatThrownBy(() -> new TraceAnnotation(3, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty list");
        }

        @Test
        void theNoticeListIsDefensivelyCopied() {
            List<Notice> mutable = new ArrayList<>();
            mutable.add(Notice.of(Severity.INFO, "First"));
            TraceAnnotation annotation = new TraceAnnotation(1, mutable);

            mutable.add(Notice.of(Severity.ACTION_REQUIRED, "Smuggled in later"));

            assertThat(annotation.notices()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Notice")
    class Notices {

        @Test
        void aNoticeCannotAbortBecauseNoSeverityIsAnError() {
            // O-27. If an error value ever appears here, something will start branching on it.
            assertThat(Severity.values())
                    .containsExactly(
                            Severity.INFO,
                            Severity.ATTENTION,
                            Severity.ACTION_REQUIRED,
                            Severity.UNVERIFIED_RULE);
        }

        @Test
        void aNoticeNobodyCanReadIsRejected() {
            assertThatThrownBy(() -> Notice.of(Severity.INFO, "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a notice");
        }

        @Test
        void aNoticeMayAriseFromNoRuleAtAll() {
            assertThat(Notice.of(Severity.INFO, "Computed under the new regime").relatedRules())
                    .isEmpty();
        }

        @Test
        void theRelatedRulesAreDefensivelyCopied() {
            List<RuleId> mutable = new ArrayList<>();
            mutable.add(HRA);
            Notice notice = new Notice(Severity.ATTENTION, "Check this", mutable);

            mutable.add(STD);

            assertThat(notice.relatedRules()).containsExactly(HRA);
        }
    }

    @Nested
    @DisplayName("StepKind")
    class Kinds {

        @Test
        void theSevenKindsAreM51sList() {
            assertThat(StepKind.values())
                    .containsExactly(
                            StepKind.INCOME,
                            StepKind.EXEMPTION,
                            StepKind.DEDUCTION,
                            StepKind.SET_OFF,
                            StepKind.TAX,
                            StepKind.RELIEF,
                            StepKind.ROUNDING);
        }
    }
}
