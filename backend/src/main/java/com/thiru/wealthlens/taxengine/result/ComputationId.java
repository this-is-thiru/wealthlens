package com.thiru.wealthlens.taxengine.result;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identifies one computation.
 *
 * <p>Opaque and non-guessable, so that a receipt built from it ({@link ComputationReceipt}) can be
 * shared in a support conversation without also handing over the ability to enumerate other
 * people's computations.
 *
 * @param value a UUID in its canonical lower-case form
 */
public record ComputationId(String value) {

    private static final Pattern UUID_FORM =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    public ComputationId {
        if (value == null || !UUID_FORM.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "A ComputationId must be a lower-case UUID, but was \"" + value + "\"");
        }
    }

    /**
     * A fresh id.
     *
     * <p>The one place in {@code taxengine} that is not a pure function of its inputs — and it is
     * not arithmetic. No rule reads an id, nothing is computed from it, and it never enters
     * {@code InputsFingerprint}: two computations of the same inputs differ in their id and in
     * nothing else that matters.
     */
    public static ComputationId fresh() {
        return new ComputationId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
