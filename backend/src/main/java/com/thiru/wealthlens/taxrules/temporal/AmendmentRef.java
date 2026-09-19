package com.thiru.wealthlens.taxrules.temporal;

import com.thiru.wealthlens.taxrules.registry.VerificationStatus;
import java.net.URI;
import java.time.LocalDate;

/**
 * The instrument that introduced one version of a rule.
 *
 * <p>Each {@code RuleVersion} carries the amendment that brought it into being, so the ordered
 * version list <b>is</b> the amendment history. That is what lets a trace line say
 * <em>&ldquo;₹75,000 — s.19(1) Table Sl. 2(a), as in force for TY 2026-27. This rule has not been
 * amended since enactment&rdquo;</em> rather than quoting a figure with no account of where it came
 * from.
 *
 * <p>{@code notifiedOn} and {@code effectiveFrom} are separate fields because they differ, and the
 * gap is where retrospective amendments live. A partial citation is a misleading one, so every field
 * here is required.
 *
 * @param instrumentCitation e.g. {@code "Finance Act, 2026 (4 of 2026), s.38"}
 * @param gazetteRef the gazette identifier, e.g. {@code "CG-DL-E-31032026-271439"}
 * @param notifiedOn when the instrument was published
 * @param effectiveFrom when the change takes effect — earlier than {@code notifiedOn} if retrospective
 * @param retrospective whether it takes effect before it was notified
 * @param sourceUrl where it was read
 * @param status whether this reference was verified against the source
 */
public record AmendmentRef(
        String instrumentCitation,
        String gazetteRef,
        LocalDate notifiedOn,
        LocalDate effectiveFrom,
        boolean retrospective,
        URI sourceUrl,
        VerificationStatus status) {

    public AmendmentRef {
        if (instrumentCitation == null || instrumentCitation.isBlank()) {
            throw new IllegalArgumentException("An amendment must cite its instrument");
        }
        if (notifiedOn == null || effectiveFrom == null) {
            throw new IllegalArgumentException(
                    instrumentCitation + " must state both when it was notified and when it takes effect");
        }
        if (status == null) {
            throw new IllegalArgumentException(instrumentCitation + " must state a VerificationStatus");
        }
        if (retrospective != effectiveFrom.isBefore(notifiedOn)) {
            throw new IllegalArgumentException(
                    instrumentCitation + " is marked retrospective=" + retrospective
                            + " but takes effect " + effectiveFrom + " and was notified " + notifiedOn
                            + "; the flag and the dates must agree");
        }
    }
}
