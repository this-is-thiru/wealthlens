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

    public static final ComponentCode BASIC = new ComponentCode("BASIC");
    public static final ComponentCode BONUS = new ComponentCode("BONUS");
    public static final ComponentCode CHILDREN_EDUCATION_ALLOWANCE = new ComponentCode("CHILDREN_EDUCATION_ALLOWANCE");
    public static final ComponentCode COMMISSION = new ComponentCode("COMMISSION");
    public static final ComponentCode CONVEYANCE_ALLOWANCE = new ComponentCode("CONVEYANCE_ALLOWANCE");
    public static final ComponentCode DEARNESS_ALLOWANCE = new ComponentCode("DEARNESS_ALLOWANCE");
    public static final ComponentCode HOSTEL_ALLOWANCE = new ComponentCode("HOSTEL_ALLOWANCE");
    public static final ComponentCode HRA = new ComponentCode("HRA");
    public static final ComponentCode LTA = new ComponentCode("LTA");
    public static final ComponentCode PROFESSIONAL_TAX = new ComponentCode("PROFESSIONAL_TAX");
    public static final ComponentCode SPECIAL_ALLOWANCE = new ComponentCode("SPECIAL_ALLOWANCE");
    public static final ComponentCode TRANSPORT_ALLOWANCE_DISABLED = new ComponentCode("TRANSPORT_ALLOWANCE_DISABLED");
    public static final ComponentCode UNIFORM_ALLOWANCE = new ComponentCode("UNIFORM_ALLOWANCE");

    private ComponentCodes() {
    }
}
