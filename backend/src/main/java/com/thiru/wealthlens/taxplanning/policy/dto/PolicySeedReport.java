package com.thiru.wealthlens.taxplanning.policy.dto;

import java.util.List;

/**
 * What a tax-policy seed found and, unless a dry run, wrote (W-088u8a, D-281, D-282).
 *
 * @param dryRun whether nothing was written
 * @param auditor who asked; the auditing records the same name on every document written
 * @param parts each policy file, in the order seeded
 */
public record PolicySeedReport(boolean dryRun, String auditor, List<Part> parts) {

    public PolicySeedReport {
        parts = List.copyOf(parts);
    }

    /**
     * One policy file.
     *
     * @param name which policy the file holds
     * @param file the classpath resource it was read from
     * @param newDocuments documents written, or in a dry run that would be
     * @param alreadyPresent documents skipped because one with the same key is already on file
     */
    public record Part(String name, String file, int newDocuments, int alreadyPresent) {
    }
}
