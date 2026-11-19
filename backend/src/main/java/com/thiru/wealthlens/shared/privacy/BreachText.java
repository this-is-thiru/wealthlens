package com.thiru.wealthlens.shared.privacy;

/**
 * What each affected user is told of a breach, in plain words (DPDP Rules r.7(1)(a) – (d); W-237c). The contact,
 * r.7(1)(e), is added by the app from the privacy settings.
 *
 * @param description the breach: its nature, extent and when it occurred (a)
 * @param consequences what is likely to follow for the user (b)
 * @param mitigation what the owner has done and is doing to reduce the risk (c)
 * @param safetyMeasures what the user can do to protect themselves (d)
 */
public record BreachText(String description, String consequences, String mitigation, String safetyMeasures) {
}
