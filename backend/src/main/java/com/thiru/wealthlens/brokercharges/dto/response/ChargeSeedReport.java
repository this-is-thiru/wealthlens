package com.thiru.wealthlens.brokercharges.dto.response;

import java.util.List;

/**
 * What one run of the seeder did.
 *
 * <p>Seeding used to happen at startup, as nobody, reporting only to a log. It is now an
 * authenticated operation and says what it changed, because "run it and see" is not an acceptable
 * answer for something that writes the rate cards every user is charged against.
 *
 * <p>Skipped is the interesting list, not created. A card already on file is never overwritten, so
 * anything skipped is a card the deployment did not apply — and if it also appears in {@code drift},
 * the file and the database disagree and somebody has to decide which is right.
 *
 * @param seededBy         the authenticated caller, recorded on every document this run wrote
 * @param catalogueCreated charge codes written; codes already present are left alone
 * @param schedulesCreated rate cards written
 * @param schedulesSkipped rate cards already on file, in whatever state they were already in
 * @param instrumentsCreated scheme profiles written
 * @param instrumentsSkipped scheme profiles already on file
 * @param drift            shipped cards that do not match the database, after this run
 */
public record ChargeSeedReport(
        String seededBy,
        List<String> catalogueCreated,
        List<String> schedulesCreated,
        List<String> schedulesSkipped,
        List<String> instrumentsCreated,
        List<String> instrumentsSkipped,
        List<ChargeScheduleDrift> drift) {

    /** Whether this run changed anything at all. A second run against the same files has not. */
    public boolean wroteNothing() {
        return catalogueCreated.isEmpty() && schedulesCreated.isEmpty() && instrumentsCreated.isEmpty();
    }
}
