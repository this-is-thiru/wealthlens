package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.registry.DraftOrFinal;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationStatus;
import com.thiru.wealthlens.taxengine.rules.temporal.AmendmentRef;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.Presence;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import tools.jackson.databind.JsonNode;

/**
 * The leaf values every pack row carries: where a rule came from, and where it lives in each Act.
 *
 * <p>Each reader converts and then <b>constructs the domain type</b>, so the type's own invariants
 * run on pack data — a {@code Provenance} that is not {@code VERIFIED} and has no
 * {@code whatToCheck} is rejected here by invariant I1, without this class knowing about I1.
 */
public final class ValueJson {

    private ValueJson() {
    }

    /** Where a rule row was read from. */
    public static Provenance provenance(JsonNode node, String path) {
        return PackJson.construct(path, () ->
        new Provenance(
                        PackJson.uri(node, "sourceUrl", path),
                        PackJson.text(node, "sourceDescription", path),
                        PackJson.enumValue(node, "draftOrFinal", path, DraftOrFinal.class),
                        PackJson.date(node, "accessedOn", path),
                        PackJson.enumValue(node, "status", path, VerificationStatus.class),
                        PackJson.optionalText(node, "whatToCheck", path).orElse(null)));
    }

    /** Where a rule lives in one Act. */
    public static Provision provision(JsonNode node, String path) {
        Presence presence = PackJson.enumValue(node, "presence", path, Presence.class);
        String instrument = PackJson.text(node, "instrument", path);
        return PackJson.construct(path, () ->
        switch (presence) {
                    case PRESENT -> Provision.present(instrument, PackJson.text(node, "reference", path));
                    case ABSENT_IN_THIS_ACT -> Provision.absent(instrument);
                    case NOT_YET_VERIFIED -> Provision.notYetVerified(instrument);
                });
    }

    /** The instrument that introduced a rule version. */
    public static AmendmentRef amendment(JsonNode node, String path) {
        return PackJson.construct(path, () ->
        new AmendmentRef(
                        PackJson.text(node, "instrumentCitation", path),
                        PackJson.text(node, "gazetteRef", path),
                        PackJson.date(node, "notifiedOn", path),
                        PackJson.date(node, "effectiveFrom", path),
                        PackJson.bool(node, "retrospective", path),
                        PackJson.uri(node, "sourceUrl", path),
                        PackJson.enumValue(node, "status", path, VerificationStatus.class)));
    }

    /** A window, open-ended where {@code to} is absent. */
    public static DateRange dateRange(JsonNode node, String path) {
        return PackJson.construct(path, () ->
        new DateRange(
                        PackJson.date(node, "from", path), PackJson.optionalDate(node, "to", path)));
    }
}
