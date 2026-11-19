package com.thiru.wealthlens.taxengine.input;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.provider.Arguments;

/**
 * One case of an input record's refusal, for a parameterised test: the field made bad, the construction with only that
 * field bad, and the fragment of the record's own message it must carry (W-240a).
 */
final class InputRefusals {

    private InputRefusals() {
    }

    static Arguments refusal(String badField, ThrowingCallable construct, String message) {
        return Arguments.of(badField, construct, message);
    }

    /**
     * Every non-primitive component of {@code valid} made null on its own, through the record's canonical constructor. Each
     * must be refused as {@code "<Record>.<component> may not be null"}, the contract of a record that names a missing part
     * (W-240b C1a); a component added later is checked with no new case.
     */
    static Stream<Arguments> eachComponentNulled(Record valid) throws NoSuchMethodException {
        RecordComponent[] components = valid.getClass().getRecordComponents();
        Constructor<?> canonical = valid.getClass()
                .getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
        String record = valid.getClass().getSimpleName();
        return IntStream.range(0, components.length)
                .filter(nulled -> !components[nulled].getType().isPrimitive())
                .mapToObj(nulled -> refusal(components[nulled].getName() + " null",
                        () -> construct(canonical, valid, components, nulled),
                        record + "." + components[nulled].getName() + " may not be null"));
    }

    private static void construct(Constructor<?> canonical, Record valid, RecordComponent[] components, int nulled)
            throws Throwable {
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            arguments[index] = index == nulled ? null : components[index].getAccessor().invoke(valid);
        }
        try {
            canonical.newInstance(arguments);
        } catch (InvocationTargetException refused) {
            throw refused.getCause();
        }
    }
}
