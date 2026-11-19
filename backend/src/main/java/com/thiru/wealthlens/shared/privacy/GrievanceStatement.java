package com.thiru.wealthlens.shared.privacy;

/**
 * What a user writes when raising a grievance, or what the owner writes when answering it (W-237b).
 *
 * @param text the grievance or the answer, in the writer's words
 */
public record GrievanceStatement(String text) {
}
