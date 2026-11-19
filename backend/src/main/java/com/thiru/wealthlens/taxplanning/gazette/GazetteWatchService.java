package com.thiru.wealthlens.taxplanning.gazette;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/** The owner's side of the Gazette watch: reading its leads and saying where it starts (W-236 C3a, D1a). */
@Service
@Log4j2
@RequiredArgsConstructor
public class GazetteWatchService {

    /** The first year the e-Gazette's numbered folders hold. */
    private static final int FIRST_FOLDER_YEAR = 2009;

    private final GazetteLeadRepository leads;

    private final GazetteCursorRepository cursors;

    private final Clock clock;

    public List<GazetteLead> leads() {
        return leads.findAllByOrderByFoundAtDesc();
    }

    /**
     * Sets where the watch starts, recording who set it.
     *
     * @throws IllegalArgumentException where the year has no folder yet or before the e-Gazette's, or the number is not
     *     positive
     */
    public GazetteCursor seed(GazetteCursorRequest request, String caller) {
        int thisYear = LocalDateTime.now(clock).getYear();
        if (request.year() < FIRST_FOLDER_YEAR || request.year() > thisYear) {
            throw new IllegalArgumentException("The year must be a Gazette folder from " + FIRST_FOLDER_YEAR + " to "
                    + thisYear + ", but was " + request.year());
        }
        if (request.lastNumber() <= 0) {
            throw new IllegalArgumentException("The last number read must be positive, but was " + request.lastNumber());
        }
        GazetteCursor cursor = new GazetteCursor();
        cursor.setId(GazetteCursor.ID);
        cursor.setYear(request.year());
        cursor.setLastNumber(request.lastNumber());
        cursor.setUpdatedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        cursor.setUpdatedBy(caller);
        log.info("Gazette watch cursor set to {}/{} by {}", request.year(), request.lastNumber(), caller);
        return cursors.save(cursor);
    }
}
