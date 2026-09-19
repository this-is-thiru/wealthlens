package com.thiru.wealthlens.taxrules.temporal;

import com.thiru.wealthlens.taxrules.cap.CapShape;
import com.thiru.wealthlens.taxrules.condition.Condition;
import com.thiru.wealthlens.taxrules.pack.RegimeApplicability;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import java.util.List;

/**
 * What a rule said during one window of time.
 *
 * <p>The rule <em>id</em> is the contract and never changes; this is what changed underneath it. A
 * Budget that moves the standard deduction from &#8377;50,000 to &#8377;75,000 does not create a new
 * rule — it appends a version, and every stored computation that cited the old one still resolves.
 *
 * <p>Both Acts' provisions are carried because the product computes two tax years governed by
 * different statutes, and {@link #introducedBy} makes the ordered version list an amendment history
 * rather than a pile of values.
 *
 * @param validity when this version was in force
 * @param regimes which regimes it applies in
 * @param cap the limit it imposes; may be null where the rule states a condition and no amount
 * @param conditions what must hold for it to apply; empty where it always does
 * @param provision1961 where it lives in the Income-tax Act 1961
 * @param provision2025 where it lives in the Income-tax Act 2025
 * @param introducedBy the instrument that brought this version into being
 * @param provenance the source this version was read from
 */
public record RuleVersion(
        Validity validity,
        RegimeApplicability regimes,
        CapShape cap,
        List<Condition> conditions,
        Provision provision1961,
        Provision provision2025,
        AmendmentRef introducedBy,
        Provenance provenance) {

    public RuleVersion {
        if (validity == null) {
            throw new IllegalArgumentException("A rule version must say when it was in force");
        }
        if (regimes == null) {
            throw new IllegalArgumentException("A rule version must say which regimes it applies in");
        }
        if (provision1961 == null || provision2025 == null) {
            throw new IllegalArgumentException(
                    "A rule version must state its place in both Acts; use Provision.absent or "
                            + "Provision.notYetVerified rather than leaving one out");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("A rule version must say where it was read from");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
