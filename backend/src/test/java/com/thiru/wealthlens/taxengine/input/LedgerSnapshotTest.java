package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every part of {@link LedgerSnapshot} is refused when missing, naming the part (W-240b). */
class LedgerSnapshotTest {

    static Stream<Arguments> refusals() throws NoSuchMethodException {
        return InputRefusals.eachComponentNulled(LedgerSnapshot.empty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    void construct_whenOneComponentIsNull_isRefusedNamingIt(String badField, ThrowingCallable construct, String message) {
        // When / Then
        assertThatThrownBy(construct).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
