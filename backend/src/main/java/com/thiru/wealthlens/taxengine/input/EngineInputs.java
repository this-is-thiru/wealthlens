package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;

/**
 * The complete input to a computation.
 *
 * <h2>The engine never fetches</h2>
 *
 * <p>{@code DR 23} A3, defect G-2. {@code X1.2} said {@code taxengine} is pure and cannot reach a
 * repository; {@code 17} L3 then called {@code history.of(profileId, regimeCode)} inside regime
 * resolution. Both could not be true, and this record is the resolution: <b>everything the engine
 * may read arrives here</b>, assembled by {@code taxplanning} from persistence.
 *
 * <p>That is not tidiness. The optimiser runs thousands of evaluations inside one request, and a
 * single repository call inside a node turns those into thousands of round trips. It is also what
 * makes a computation reproducible years later — a result that re-read the database would not be.
 * {@code ArchitectureTest.taxengine_fetches_nothing} enforces it, and this record is what makes the
 * rule satisfiable rather than merely aspirational.
 *
 * <h2>Deliberately not the draft</h2>
 *
 * <p>A3 again: this is <b>not</b> {@code DraftInputs} ({@code 11-filing-drafts.md} F4).
 * {@code DraftInputs} is what a user typed; this is that <i>plus</i> every cross-cutting fact the
 * rules can read — the election history, the cross-year ledgers, the pinned portfolio summary.
 * Under decision <b>D-024</b> the entered side arrives as {@link EnteredFacts}, a
 * {@code taxengine}-owned projection, because {@code DraftInputs} is a {@code taxplanning} type the
 * engine may not hold. Blocker <b>B-003</b> records why A3's own component list could not compile.
 *
 * <h2>Completeness is the point</h2>
 *
 * <p>Every component is mandatory. {@code W-038}'s {@code InputsFingerprint} is derived from this
 * record and is the cache key, and A3's inference is that the fingerprint works precisely because
 * this is <b>one complete object</b> — <i>"a field added to {@code EngineInputs} is in the key
 * automatically, which is the only way that invariant survives"</i>. A component left out is an
 * input the key cannot see, so two computations differing only in it would collide and one would be
 * served the other's answer. That is why {@link EnteredFacts} is declared and empty rather than
 * omitted, and why nothing here is optional.
 *
 * <p>There is no {@code fingerprint()} method. A3 shows one; it returns {@code InputsFingerprint},
 * which {@code W-038} owns and builds next (decision <b>D-025</b>).
 *
 * @param taxYear the year being computed
 * @param regime the regime being computed under
 * @param temporal the temporal coordinates, {@code asOf} included explicitly — the engine reads no
 *     clock, so the instant of evaluation is an input like any other
 * @param entered what the taxpayer entered
 * @param taxpayer who they are
 * @param family the people whose facts per-person rules read
 * @param ledgers the cross-year state a single year cannot hold
 * @param elections where they stand with each regime — passed in, never fetched
 * @param capitalGains the pinned portfolio summary
 * @param filing what was filed and paid, and when
 */
public record EngineInputs(
        TaxYearId taxYear,
        RegimeCode regime,
        TemporalContext temporal,
        EnteredFacts entered,
        TaxpayerFacts taxpayer,
        FamilySnapshot family,
        LedgerSnapshot ledgers,
        RegimeElectionSnapshot elections,
        CapitalGainsInput capitalGains,
        FilingFacts filing) {

    public EngineInputs {
        requirePresent(taxYear, "taxYear");
        requirePresent(regime, "regime");
        requirePresent(temporal, "temporal");
        requirePresent(entered, "entered");
        requirePresent(taxpayer, "taxpayer");
        requirePresent(family, "family");
        requirePresent(ledgers, "ledgers");
        requirePresent(elections, "elections");
        requirePresent(capitalGains, "capitalGains");
        requirePresent(filing, "filing");

        if (!taxYear.equals(temporal.taxYear())) {
            throw new IllegalArgumentException(
                    "EngineInputs.taxYear is " + taxYear + " but its TemporalContext is for "
                            + temporal.taxYear()
                            + "; the year is stated twice and the two must agree, or rules keyed on"
                            + " one will disagree with rules keyed on the other");
        }
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "EngineInputs." + name + " may not be null. Every component is mandatory: the"
                            + " fingerprint is derived from this record and is the cache key, so a"
                            + " missing component is an input the key cannot see");
        }
    }

    /** The as-of instant, which the engine reads instead of a clock. */
    public java.time.LocalDate asOfDate() {
        return temporal.asOfDate();
    }
}
