package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * Names an amount a cap may be computed as a percentage of, such as {@code SALARY_BASIC_PLUS_DA}.
 *
 * <p>A cap shape carries a base <em>identifier</em>, never a base <em>amount</em>. Thirty per cent
 * of the annual value and ten per cent of salary are the same shape applied to different bases, and
 * a rule pack is data: it can say which base a rule reads, but it cannot hold the taxpayer's figure.
 * The amount arrives later, in a {@code CapContext}, resolved for the taxpayer being computed.
 *
 * <p>That split is also what makes {@code LeastOf} expressible. House rent allowance is the least of
 * three limbs, each over a different base; with a base identifier per limb that is one nested shape,
 * and with a single implicit base it is a special case in code.
 *
 * <p>Like {@link ComponentCode}, the set of bases lives in the rule pack rather than in a Java enum
 * — a new base is a data change, and the loader rejects a rule naming one the pack does not define.
 */
public record BaseId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private static final String SHAPE =
            "upper-case with underscores, 2-64 characters, e.g. SALARY_BASIC_PLUS_DA";

    public BaseId {
        value = Identifiers.require(value, PATTERN, "BaseId", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
