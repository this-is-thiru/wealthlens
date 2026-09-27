package com.thiru.wealthlens.taxengine.rules.generated;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;

/**
 * GENERATED — do not edit. Source: {@code src/main/resources/rulepacks/&#42;&#42;/*.json}.
 *
 * <p>Every node in the timeline's pipeline catalogue. A node referenced from Java
 * is referenced by constant, so a slot renamed in the timeline breaks its callers
 * at compile time rather than as an empty value at run time.
 *
 * <p>Regenerate with {@code ./mvnw test -pl backend -Dtest=GeneratedConstantsTest
 * -Dtaxengine.rules.regenerate=true}. {@code GeneratedConstantsTest} fails if this file and the
 * packs disagree, so a stale copy cannot survive a build.
 */
public final class NodeIds {

    public static final NodeId INPUTS_COMPONENTS = new NodeId("inputs.components");
    public static final NodeId ROUND_NETPAYABLE = new NodeId("round.netpayable");
    public static final NodeId ROUND_TOTALINCOME = new NodeId("round.totalincome");
    public static final NodeId SALARY_BASE = new NodeId("salary.base");
    public static final NodeId SALARY_DEDUCTIONS = new NodeId("salary.deductions");
    public static final NodeId SALARY_GROSS = new NodeId("salary.gross");
    public static final NodeId TAX_AFTERREBATE = new NodeId("tax.afterrebate");
    public static final NodeId TAX_BYBASIS = new NodeId("tax.bybasis");
    public static final NodeId TAX_CESS = new NodeId("tax.cess");
    public static final NodeId TAX_NETPAYABLE = new NodeId("tax.netpayable");
    public static final NodeId TAX_SURCHARGE = new NodeId("tax.surcharge");
    public static final NodeId TOTALINCOME = new NodeId("totalincome");

    private NodeIds() {
    }
}
