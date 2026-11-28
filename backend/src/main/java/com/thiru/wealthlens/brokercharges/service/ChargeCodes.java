package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.regex.Pattern;

/**
 * What a charge code is allowed to look like.
 *
 * <p>A charge code is not merely an identifier — it is a stored key: {@code UserChargeEntity.amountByCode}
 * ({@code user_charge_amounts.charge_code}) and {@code ChargeSummaryReport.amountByCode} (inside the P&amp;L's JSON) are both
 * keyed by it and matched exactly, so every stored code keeps one shape, {@code [A-Z][A-Z0-9_]*}. The shape dates from
 * MongoDB, which refused a field name containing {@code .} or beginning with {@code $}; every code stored since has it.
 *
 * <p>Case is fixed for a second reason. The catalogue's unique index is case-sensitive, so {@code BROKERAGE}
 * and {@code brokerage} would be two catalogue rows for one charge. Rules name codes exactly, so the
 * second would be unreachable — and a charge nobody can name is worse than one that was refused.
 *
 * <p>Checked where codes enter, not where they are used: the catalogue is the single gate, and a
 * rule may only name a code the catalogue already holds.
 */
public final class ChargeCodes {

    private static final Pattern VALID = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private ChargeCodes() {
    }

    /**
     * @throws BadRequestException if the code does not have the one shape every stored code has, or
     *                             differs only by case from how it must be written
     */
    public static void validate(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("A charge code must not be blank");
        }
        if (!code.equals(code.toUpperCase())) {
            throw new BadRequestException(
                    "Charge code " + code + " must be upper case: the catalogue's unique index is "
                            + "case-sensitive, so two casings would be two catalogue rows for one charge");
        }
        if (!VALID.matcher(code).matches()) {
            throw new BadRequestException(
                    "Charge code " + code + " must match [A-Z][A-Z0-9_]* — upper case, digits and underscores "
                            + "only: every stored code has this one shape");
        }
    }
}
