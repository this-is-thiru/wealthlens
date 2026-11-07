package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.regex.Pattern;

/**
 * What a charge code is allowed to look like.
 *
 * <p>A charge code is not merely an identifier — it becomes a <b>field name in MongoDB</b>, because
 * {@code UserChargeEntity.amountByCode} and {@code ChargeSummaryReport.amountByCode} are both keyed
 * by it. MongoDB rejects a field name containing {@code .} or beginning with {@code $}. A code like
 * {@code MTF.INTEREST} therefore passes the catalogue check, prices correctly, and then fails when
 * the row is written: the charge is computed and then lost, which is the failure mode this whole
 * design exists to avoid.
 *
 * <p>Case is fixed for a second reason. Mongo's unique index is case-sensitive, so {@code BROKERAGE}
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
     * @throws BadRequestException if the code could not be used as a MongoDB field name, or differs
     *                             only by case from how it must be written
     */
    public static void validate(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("A charge code must not be blank");
        }
        if (!code.equals(code.toUpperCase())) {
            throw new BadRequestException(
                    "Charge code " + code + " must be upper case: Mongo's unique index is "
                            + "case-sensitive, so two casings would be two catalogue rows for one charge");
        }
        if (!VALID.matcher(code).matches()) {
            throw new BadRequestException(
                    "Charge code " + code + " is not usable as a field name: a code is stored as the key of "
                            + "amount_by_code, so it must match [A-Z][A-Z0-9_]* — no dots, no '$', no spaces");
        }
    }
}
