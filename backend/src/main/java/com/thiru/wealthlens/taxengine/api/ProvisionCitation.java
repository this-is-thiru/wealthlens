package com.thiru.wealthlens.taxengine.api;

/**
 * One section of an Income-tax Act that a shipped rule cites — what a reader outside the engine needs to say which rules
 * a new instrument may touch (W-236). A fact read from the packs, never a verdict that the rule is affected.
 *
 * @param act the Act's year, {@code 1961} or {@code 2025}
 * @param section the section number with its letters, as the reference writes it ({@code 19}, {@code 80CCD})
 * @param ruleId the citing rule
 */
public record ProvisionCitation(String act, String section, String ruleId) {
}
