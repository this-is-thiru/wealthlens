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

    public static final RuleId RATE_ROUND_PAYABLE = new RuleId("RATE-ROUND-PAYABLE");
    public static final RuleId RATE_ROUND_TOTAL_INCOME = new RuleId("RATE-ROUND-TOTAL-INCOME");
    public static final RuleId RATE_SLAB_FIRST_SCHEDULE = new RuleId("RATE-SLAB-FIRST-SCHEDULE");
    public static final RuleId RATE_SLAB_FIRST_SCHEDULE_AGE_60TO79 = new RuleId("RATE-SLAB-FIRST-SCHEDULE-AGE-60TO79");
    public static final RuleId RATE_SLAB_FIRST_SCHEDULE_AGE_80 = new RuleId("RATE-SLAB-FIRST-SCHEDULE-AGE-80");
    public static final RuleId RATE_SLAB_NEW_TAX_REGIME = new RuleId("RATE-SLAB-NEW-TAX-REGIME");
    public static final RuleId REBATE_NEW_TAX_REGIME = new RuleId("REBATE-NEW-TAX-REGIME");
    public static final RuleId REBATE_NEW_TAX_REGIME_EXCESS = new RuleId("REBATE-NEW-TAX-REGIME-EXCESS");
    public static final RuleId REBATE_RESIDENT_INDIVIDUAL = new RuleId("REBATE-RESIDENT-INDIVIDUAL");

    private RuleIds() {
    }
}
