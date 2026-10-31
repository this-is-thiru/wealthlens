package com.thiru.wealthlens.taxengine.rules.temporal;

import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.pack.RegimeApplicability;
import com.thiru.wealthlens.taxengine.rules.registry.LegalStatus;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.relief.RuleRelief;
import com.thiru.wealthlens.taxengine.rules.rounding.RuleRounding;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * @param cap the limit it imposes; empty where the rule states a condition, relief, rounding or definition and
 *     limits nothing
 * @param relief the relief it grants; never beside a cap, because a rule either limits an amount or
 *     takes one off the tax, and one that did both would leave its reader to guess the order
 * @param rounding the step it rounds an amount to; never beside a cap or a relief, for the same reason
 * @param definition what it says an amount is (D-091); never beside a cap, relief or rounding, for the same
 *     reason
 * @param conditions what must hold for it to apply; empty where it always does
 * @param provision1961 where it lives in the Income-tax Act 1961
 * @param provision2025 where it lives in the Income-tax Act 2025
 * @param introducedBy the instrument that brought this version into being
 * @param legalStatus what the law says about this version; {@link LegalStatus#PENDING_NOTIFICATION}
 *     exactly when {@code validity} is a {@link Validity.PendingNotification}, which names the interim rule
 * @param publicationStatus where the version is in our workflow; only {@code PUBLISHED} enters a pack
 * @param provenance the source this version was read from
 */
public record RuleVersion(
        Validity validity,
        RegimeApplicability regimes,
        Optional<CapShape> cap,
        Optional<RuleRelief> relief,
        Optional<RuleRounding> rounding,
        Optional<RuleDefinition> definition,
        List<Condition> conditions,
        Provision provision1961,
        Provision provision2025,
        AmendmentRef introducedBy,
        LegalStatus legalStatus,
        PublicationStatus publicationStatus,
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
        if (legalStatus == null || publicationStatus == null) {
            throw new IllegalArgumentException(
                    "A rule version must state its legal status and its publication status");
        }
        requireStatedOrEmpty(cap, "cap", "states none");
        requireStatedOrEmpty(relief, "relief", "grants none");
        requireStatedOrEmpty(rounding, "rounding", "states none");
        requireStatedOrEmpty(definition, "definition", "states none");
        requireAtMostOneOf(cap, relief, rounding, definition);
        requirePendingNotificationStatedTwiceAlike(validity, legalStatus);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    /** What a version may leave out is {@code Optional.empty()}, never a null passed across the boundary. */
    private static void requireStatedOrEmpty(Optional<?> value, String what, String whenAbsent) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "A rule version's " + what + " is Optional.empty() when it " + whenAbsent);
        }
    }

    /**
     * A rule limits an amount, grants a relief, rounds an amount or defines one; one doing two would leave its
     * reader to guess the order.
     */
    private static void requireAtMostOneOf(
            Optional<CapShape> cap,
            Optional<RuleRelief> relief,
            Optional<RuleRounding> rounding,
            Optional<RuleDefinition> definition) {
        List<String> stated = new ArrayList<>();
        cap.ifPresent(present -> stated.add("a cap"));
        relief.ifPresent(present -> stated.add("a relief"));
        rounding.ifPresent(present -> stated.add("a rounding"));
        definition.ifPresent(present -> stated.add("a definition"));
        if (stated.size() > 1) {
            String listed = stated.size() == 2
                    ? "both " + stated.get(0) + " and " + stated.get(1)
                    : String.join(", ", stated.subList(0, stated.size() - 1)) + " and " + stated.getLast();
            throw new IllegalArgumentException("A rule version has " + listed
                    + "; a rule limits an amount, grants a relief, rounds an amount or defines one, only one of them");
        }
    }

    /**
     * Pending notification is said twice — by the validity, which names the interim rule, and by the
     * legal status — so the two may not disagree (D-052).
     */
    private static void requirePendingNotificationStatedTwiceAlike(Validity validity, LegalStatus legalStatus) {
        boolean pendingValidity = validity instanceof Validity.PendingNotification;
        boolean pendingStatus = legalStatus == LegalStatus.PENDING_NOTIFICATION;
        if (pendingStatus && !pendingValidity) {
            throw new IllegalArgumentException(
                    "A PENDING_NOTIFICATION version must have a PendingNotification validity naming its"
                            + " interim rule, but has " + validity);
        }
        if (pendingValidity && !pendingStatus) {
            throw new IllegalArgumentException(
                    "A version with a PendingNotification validity must have legal status"
                            + " PENDING_NOTIFICATION, but has " + legalStatus);
        }
    }
}
