package com.thiru.wealthlens.taxplanning.workspace;

/** A status change; {@code regimeCode} is the regime the user says they elected when acting on a draft. */
public record DraftStatusRequest(DraftStatus status, String regimeCode) {
}
