package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import java.util.Optional;

/**
 * What a cap computed, and what stopped it going higher.
 *
 * <p>The second field is the reason this type exists. A cap that returns only a number can be shown
 * to a taxpayer but cannot be explained to one: &ldquo;your house rent allowance exemption is
 * &#8377;1,20,000&rdquo; is an answer, and &ldquo;&hellip;because rent paid over ten per cent of
 * salary is the smallest of the three limbs&rdquo; is advice. Only the second tells the reader that
 * paying more rent would help and that a larger allowance alone would not.
 *
 * @param value the capped amount
 * @param limitedBy the binding constraint, named as the rulebook names it — the winning limb of a
 *     {@code LeastOf}, {@code "ceiling"} where an aggregate ceiling bound, or the shape's own name
 *     where nothing did
 * @param reason the same as facts, for the explanation to put into words (W-327a1, D-679); empty for a
 *     {@code LeastOf}, whose limb the content words by name, and for a cap split across a year's dates
 */
public record CapResult(Money value, String limitedBy, Optional<Reason> reason) {

    /** A result whose {@code limitedBy} is its whole reason: a least-of's limb, or a date-split cap's parts. */
    public CapResult(Money value, String limitedBy) {
        this(value, limitedBy, Optional.empty());
    }

    public CapResult {
        if (value == null) {
            throw new IllegalArgumentException("A CapResult value may not be null");
        }
        if (limitedBy == null || limitedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "A CapResult must name what limited it; got " + (limitedBy == null ? "null" : "blank"));
        }
        if (reason == null) {
            throw new IllegalArgumentException("A CapResult's reason may not be null; pass Optional.empty()");
        }
    }
}
