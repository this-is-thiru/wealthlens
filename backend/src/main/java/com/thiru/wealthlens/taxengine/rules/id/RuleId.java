package com.thiru.wealthlens.taxengine.rules.id;

import java.util.regex.Pattern;

/**
 * The stable identity of one statutory rule, such as {@code SAL-HRA-001}.
 *
 * <p>A rule ID never changes. The provision it points at does — {@code 80C} became {@code s.123}
 * when the Income-tax Act 2025 replaced the 1961 Act, and nineteen of the twenty-four differences
 * between the two tax years this product ships are renumberings of that kind. Because the ID is
 * stable, every test, trace line and golden case survives the change; only the rule row's provision
 * field moves.
 */
public record RuleId(String value) {

    /**
     * A head of two to eight capitals, then one to five hyphenated segments.
     *
     * <p>Five, not three: {@code SAL-ALW-TRANSPORT-DIS-2026} and {@code RATE-SLAB-OLD-2026-A} are
     * real rule IDs with four segments. See the erratum at {@code 02-TECH-SPEC.md} T3.
     */
    private static final Pattern PATTERN = Pattern.compile("^[A-Z]{2,8}(-[A-Z0-9]{1,12}){1,5}$");

    private static final String SHAPE =
            "2-8 capitals then 1-5 hyphenated segments, e.g. SAL-HRA-001";

    public RuleId {
        value = Identifiers.require(value, PATTERN, "RuleId", SHAPE);
    }

    @Override
    public String toString() {
        return value;
    }
}
