package com.thiru.wealthlens.taxrules.generated;

import com.thiru.wealthlens.taxrules.id.RuleId;

/**
 * GENERATED — do not edit. Source: {@code src/main/resources/rulepacks/&#42;&#42;/*.json}.
 *
 * <p>Every rule id in every pack. A rule referenced from Java is referenced by
 * constant, so a misspelling does not compile, and a rule deleted from the packs
 * breaks its callers visibly rather than at run time.
 *
 * <p>Regenerate with {@code ./mvnw test -pl backend -Dtest=GeneratedConstantsTest
 * -Dtaxrules.regenerate=true}. {@code GeneratedConstantsTest} fails if this file and the
 * packs disagree, so a stale copy cannot survive a build.
 */
public final class RuleIds {

    public static final RuleId RATE_SLAB_NEW_2026 = new RuleId("RATE-SLAB-NEW-2026");
    public static final RuleId RATE_SLAB_OLD_2026 = new RuleId("RATE-SLAB-OLD-2026");

    private RuleIds() {
    }
}
