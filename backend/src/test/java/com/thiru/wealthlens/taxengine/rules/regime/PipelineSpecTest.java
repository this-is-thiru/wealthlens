package com.thiru.wealthlens.taxengine.rules.regime;

import static com.thiru.wealthlens.taxengine.rules.RuleRefusals.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** What {@link PipelineSpec} refuses (W-240c). */
class PipelineSpecTest {

    @Test
    void construct_whenEveryFieldIsValid_isAccepted() {
        // When
        PipelineSpec valid = new PipelineSpec(List.of(new NodeId("tax.bybasis")));

        // Then
        assertThat(valid.nodeRefs()).hasSize(1);
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                refusal("nodeRefs null", () -> new PipelineSpec(null),
                        "A pipeline must name at least one node"),
                refusal("nodeRefs empty", () -> new PipelineSpec(List.of()),
                        "A pipeline must name at least one node"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneFieldIsBad_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
