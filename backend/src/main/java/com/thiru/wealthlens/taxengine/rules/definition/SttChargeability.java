package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * What a row of {@code RS-STT-CHARGEABLE} says of one side of a disposal whose STT no record shows: whether the
 * transaction was chargeable to securities transaction tax under the Finance (No. 2) Act, 2004, Chapter VII (D-298).
 *
 * <p>A code, not a flag, so the capital-gains part rows decide what they accept — s.196's "chargeable", s.198's "paid"
 * by the owner's ruling (D-294) — and a later state is a new code in data.
 *
 * @param code the side's state, such as {@code CHARGEABLE}
 */
public record SttChargeability(String code) implements RuleDefinition {

    public SttChargeability {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("An STT-chargeability row must name its code");
        }
    }
}
