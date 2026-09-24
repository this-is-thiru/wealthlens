package com.thiru.wealthlens.taxengine.rules.generated;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;

/**
 * GENERATED — do not edit. Source: {@code src/main/resources/rulepacks/&#42;&#42;/*.json}.
 *
 * <p>Every rule id in every pack. A rule referenced from Java is referenced by
 * constant, so a misspelling does not compile, and a rule deleted from the packs
 * breaks its callers visibly rather than at run time.
 *
 * <p>Regenerate with {@code ./mvnw test -pl backend -Dtest=GeneratedConstantsTest
 * -Dtaxengine.rules.regenerate=true}. {@code GeneratedConstantsTest} fails if this file and the
 * packs disagree, so a stale copy cannot survive a build.
 */
public final class RuleIds {

    public static final RuleId RATE_SLAB_FIRST_SCHEDULE = new RuleId("RATE-SLAB-FIRST-SCHEDULE");
    public static final RuleId RATE_SLAB_NEW_TAX_REGIME = new RuleId("RATE-SLAB-NEW-TAX-REGIME");

    private RuleIds() {
    }
}
