/**
 * The identifier types — {@code TaxYearId}, {@code RuleId}, {@code RegimeCode} and the rest. A named
 * interface because callers address years, rules and regimes by them; {@code propagate} does not
 * carry these through {@code input}/{@code result}, as the W-045b2 probe showed (decision D-048).
 */
@org.springframework.modulith.NamedInterface("id")
package com.thiru.wealthlens.taxengine.rules.id;
