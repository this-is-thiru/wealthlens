package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * What a row of {@code RS-CG-CHARGE} says of one charge on one side of a disposal (W-162a, D-375): which charges reduce
 * a capital gain is the law's, and the Acts name only securities transaction tax, so each row states the charge codes
 * and side it covers as conditions and the limb here — a charge the catalogue learns later is a new row, not new Java.
 *
 * @param limb where the row's charges stand in computing the gain
 */
public record ChargeDeduction(ChargeDeductionLimb limb) implements RuleDefinition {

    public ChargeDeduction {
        if (limb == null) {
            throw new IllegalArgumentException("A charge-deduction row must name its limb");
        }
    }
}
