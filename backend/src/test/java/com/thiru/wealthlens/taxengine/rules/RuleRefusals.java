package com.thiru.wealthlens.taxengine.rules;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.provider.Arguments;

/**
 * One case of a rule-vocabulary record's refusal, for a parameterised test: the field made bad, the construction with
 * only that field bad, and the fragment of the record's own message it must carry (W-240c). Public because the records
 * live in the vocabulary's sub-packages; the input records' twin is {@code InputRefusals}.
 */
public final class RuleRefusals {

    private RuleRefusals() {
    }

    public static Arguments refusal(String badField, ThrowingCallable construct, String message) {
        return Arguments.of(badField, construct, message);
    }
    /**
     * A copy of {@code valid} with one component replaced, built through the record's canonical constructor so its checks
     * run: how a record of more than seven parts is made bad one part at a time without a constructor call per case.
     *
     * @throws Throwable whatever the constructor throws, unwrapped
     */
    @SuppressWarnings("unchecked")
    public static <R extends Record> R withComponent(R valid, String component, Object value) throws Throwable {
        RecordComponent[] components = valid.getClass().getRecordComponents();
        if (Arrays.stream(components).noneMatch(each -> each.getName().equals(component))) {
            throw new IllegalStateException(valid.getClass().getSimpleName() + " has no component " + component);
        }
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            arguments[index] = components[index].getName().equals(component) ? value
                    : components[index].getAccessor().invoke(valid);
        }
        try {
            return (R) valid.getClass()
                    .getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new))
                    .newInstance(arguments);
        } catch (InvocationTargetException refused) {
            throw refused.getCause();
        }
    }
}
