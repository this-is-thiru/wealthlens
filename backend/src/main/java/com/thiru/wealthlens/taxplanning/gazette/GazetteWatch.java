package com.thiru.wealthlens.taxplanning.gazette;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxplanning.jobs.JobContext;
import com.thiru.wealthlens.taxplanning.jobs.TaxJob;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * The daily Gazette watch ({@code DR 19} E8, W-236): reads the numbers after the cursor, records a lead for each copy that
 * may change a shipped pack, and moves the cursor past each copy read. It never writes a pack.
 *
 * <p>It stops after {@code missesToStop} missing numbers in a row — the end of what is published — trying the next
 * year's folder once first where the clock's year is later, since numbers run on across years; after
 * {@code maxNumbersPerRun} numbers; or when it loses its lease. A copy it cannot fetch fails the run with the cursor still
 * before it, so the next run tries it again. With no cursor it refuses: where to start is the owner's to say.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class GazetteWatch implements TaxJob {

    private final GazetteCursorRepository cursors;

    private final GazetteLeadRepository leads;

    private final GazetteCopies copies;

    private final GazetteWatchSettings settings;

    private final TaxCalculator calculator;

    @Override
    public String name() {
        return "gazette-watch";
    }

    @Override
    public Duration timeout() {
        return Duration.ofMinutes(60);
    }

    @Override
    public void run(JobContext context) {
        Optional<GazetteCursor> found = cursors.findById(GazetteCursor.ID);
        if (found.isEmpty()) {
            context.refuse("tax_gazette_cursor", "no cursor: seed it with PUT /tax-planning/admin/gazette-cursor");
            return;
        }
        GazetteCursor cursor = found.get();
        GazetteMatcher matcher = new GazetteMatcher(settings, calculator.provisionsCited());
        int year = cursor.getYear();
        long number = cursor.getLastNumber() + 1;
        int misses = 0;
        boolean rolled = false;
        for (int read = 0; read < settings.maxNumbersPerRun() && context.holdsLease(); read++) {
            Optional<GazetteCopy> copy = copies.fetch(year, number);
            if (copy.isPresent()) {
                record(copy.get(), matcher, context);
                moveTo(cursor, year, number, context);
                misses = 0;
            } else if (++misses >= settings.missesToStop()) {
                if (rolled || LocalDateTime.now(context.clock()).getYear() <= year) {
                    break;
                }
                rolled = true;
                year++;
                number = cursor.getLastNumber();
                misses = 0;
            }
            number++;
            pause();
        }
        log.info("Job {} stopped at {}/{}", name(), cursor.getYear(), cursor.getLastNumber());
    }

    private void record(GazetteCopy copy, GazetteMatcher matcher, JobContext context) {
        Optional<GazetteLead> lead = copy.pdf().isEmpty()
                ? Optional.of(lead(copy, 0, List.of(), List.of(), "too large to read (over "
                        + settings.maxDocumentSize().toBytes() + " bytes); open it", context))
                : matcher.match(copy.pdf().get()).map(match -> lead(copy, match.page(), match.matchedTerms(),
                        match.possiblyAffected(), "names " + String.join(", ", match.matchedTerms()), context));
        lead.ifPresent(this::insert);
    }

    private void insert(GazetteLead lead) {
        try {
            leads.insert(lead);
            log.warn("Gazette lead {} (page {}): {}", lead.getUrl(), lead.getPage(), lead.getNote());
        } catch (DuplicateKeyException already) {
            log.info("Gazette lead {} is already recorded", lead.getUrl());
        }
    }

    private static GazetteLead lead(GazetteCopy copy, int page, List<String> terms, List<String> affected, String note,
            JobContext context) {
        GazetteLead lead = new GazetteLead();
        lead.setUrl(copy.url());
        lead.setYear(copy.year());
        lead.setNumber(copy.number());
        lead.setPage(page);
        lead.setMatchedTerms(terms);
        lead.setPossiblyAffected(affected);
        lead.setNote(note);
        lead.setFoundAt(context.nowUtc());
        return lead;
    }

    private void moveTo(GazetteCursor cursor, int year, long number, JobContext context) {
        cursor.setYear(year);
        cursor.setLastNumber(number);
        cursor.setUpdatedAt(context.nowUtc());
        cursor.setUpdatedBy(name());
        cursors.save(cursor);
    }

    private void pause() {
        try {
            Thread.sleep(settings.pause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The Gazette watch was interrupted", interrupted);
        }
    }
}
