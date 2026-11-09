package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import java.util.List;
import java.util.Set;

/**
 * One return form as a year's law defines it — who it is for, what bars it, and how it may be furnished (DR 21 I3,
 * W-090b). The Rules make the form set per year and keep earlier sets live (r.164(1), (14)), so a form is a row of
 * the year's pack, versioned per tax year on the timeline (D-521 C3a), never a Java constant.
 *
 * <p>Selection is a cascade in the law's own order, not a search for the simplest form: r.164(4) defines ITR-2 as
 * for an individual "not being an individual to whom sub-rule (2) applies", so a form names the earlier forms whose
 * eligibility excludes it.
 *
 * @param code the form
 * @param displayName what to call it
 * @param cascadeOrder the law's order, 1 first; a lower order is tried first
 * @param appliesTo the person types it serves; never empty
 * @param returnKinds the kinds of return it serves; never empty
 * @param positiveConditions all of which must hold for the form to apply; may be empty
 * @param bars any of which disqualifies; may be empty
 * @param excludes earlier forms that, where eligible, exclude this one
 * @param furnishing the manners it may be furnished in; never empty
 * @param provenance where it was read
 */
public record ItrFormSpec(
        ItrFormCode code,
        String displayName,
        int cascadeOrder,
        Set<PersonType> appliesTo,
        Set<ReturnKind> returnKinds,
        List<Condition> positiveConditions,
        List<ItrFormBar> bars,
        Set<ItrFormCode> excludes,
        List<FurnishingOption> furnishing,
        Provenance provenance) {

    public ItrFormSpec {
        if (code == null) {
            throw new IllegalArgumentException("A form must have a code");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(code + " must have a display name; it reaches the user");
        }
        if (cascadeOrder < 1) {
            throw new IllegalArgumentException(code + " has cascade order " + cascadeOrder + "; the first is 1");
        }
        appliesTo = nonEmpty(code, "appliesTo", appliesTo);
        returnKinds = nonEmpty(code, "returnKinds", returnKinds);
        furnishing = List.copyOf(nonEmpty(code, "furnishing", furnishing));
        positiveConditions = List.copyOf(required(code, "positiveConditions", positiveConditions));
        bars = List.copyOf(required(code, "bars", bars));
        excludes = Set.copyOf(required(code, "excludes", excludes));
        if (excludes.contains(code)) {
            throw new IllegalArgumentException(code + " excludes itself; a form is excluded only by an earlier one");
        }
        if (provenance == null) {
            throw new IllegalArgumentException(code + " must say where it was read from");
        }
    }

    private static <T> Set<T> nonEmpty(ItrFormCode code, String field, Set<T> values) {
        if (required(code, field, values).isEmpty()) {
            throw new IllegalArgumentException(code + "." + field + " is empty; such a form could never be chosen");
        }
        return Set.copyOf(values);
    }

    private static <T> List<T> nonEmpty(ItrFormCode code, String field, List<T> values) {
        if (required(code, field, values).isEmpty()) {
            throw new IllegalArgumentException(code + "." + field + " is empty; such a form could never be furnished");
        }
        return values;
    }

    private static <T> T required(ItrFormCode code, String field, T values) {
        if (values == null) {
            throw new IllegalArgumentException(code + "." + field + " may not be null; pass an empty collection");
        }
        return values;
    }
}
