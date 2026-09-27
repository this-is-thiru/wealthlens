package com.thiru.wealthlens.taxengine.rules.generated;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;

/**
 * GENERATED — do not edit. Source: {@code src/main/resources/rulepacks/&#42;&#42;/*.json}.
 *
 * <p>Every component code in every pack. Codes live in data so that adding an
 * allowance is a data change; these constants give Java the compile-time safety
 * the enum used to, without closing the vocabulary.
 *
 * <p>Regenerate with {@code ./mvnw test -pl backend -Dtest=GeneratedConstantsTest
 * -Dtaxengine.rules.regenerate=true}. {@code GeneratedConstantsTest} fails if this file and the
 * packs disagree, so a stale copy cannot survive a build.
 */
public final class ComponentCodes {

    public static final ComponentCode PROFESSIONAL_TAX = new ComponentCode("PROFESSIONAL_TAX");

    private ComponentCodes() {
    }
}
