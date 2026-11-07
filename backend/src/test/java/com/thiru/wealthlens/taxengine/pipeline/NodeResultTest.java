package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.trace.Notice;
import com.thiru.wealthlens.taxengine.trace.Severity;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceAnnotation;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A notice hangs off exactly one line the node produced (Notice, O-5; D-167). */
class NodeResultTest {

    private static final Notice NOTICE = Notice.of(Severity.ATTENTION, "about a line");

    private static TraceLine line(int sequence) {
        return TraceLine.of(sequence, NodeIds.SALARY_EXEMPTIONS, new RuleId("SAL-HOUSE-RENT-ALLOWANCE"),
                StepKind.EXEMPTION, MoneyValue.of(Money.ZERO), MoneyValue.of(Money.ZERO),
                new Provenance(URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"), "a source",
                        LocalDate.of(2026, 10, 9), VerificationLevel.SOURCE_CHECKED, null));
    }

    @Test
    void construct_whenTheAnnotationAddressesALineTheNodeEmitted_keepsIt() {
        // When
        NodeResult result = new NodeResult(MoneyValue.of(Money.ZERO), List.of(line(1), line(2)),
                List.of(TraceAnnotation.of(2, NOTICE)));

        // Then
        assertThat(result.annotations()).singleElement().extracting(TraceAnnotation::sequence).isEqualTo(2);
    }

    @Test
    void construct_whenTheAnnotationAddressesNoLine_isRefused() {
        assertThatThrownBy(() -> new NodeResult(MoneyValue.of(Money.ZERO), List.of(line(1)),
                List.of(TraceAnnotation.of(2, NOTICE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("addresses line 2, which 0 of this node's lines carry");
    }

    @Test
    void construct_whenTwoLinesCarryTheAddressedSequence_isRefused() {
        assertThatThrownBy(() -> new NodeResult(MoneyValue.of(Money.ZERO), List.of(line(1), line(1)),
                List.of(TraceAnnotation.of(1, NOTICE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("addresses line 1, which 2 of this node's lines carry");
    }

    @Test
    void construct_whenGivenOnlyAValueAndLines_saysNothing() {
        assertThat(new NodeResult(MoneyValue.of(Money.ZERO), List.of(line(1))).annotations()).isEmpty();
    }
}
