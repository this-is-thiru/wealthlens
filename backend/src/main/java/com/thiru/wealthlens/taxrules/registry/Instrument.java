package com.thiru.wealthlens.taxrules.registry;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One piece of legislation a tax year is governed by.
 *
 * <p>A tax year needs three: the Act, the Finance Act that sets its rates, and the Rules. They are
 * separate because they move separately — the Finance Act arrives in March, the Rules are notified
 * on their own date and amended several times within the year, and the Act may not change at all.
 *
 * @param assentedOn empty for subordinate legislation: Rules are <em>notified</em>, not assented, so
 *     the Income-tax Rules 2026 have a gazette reference and a commencement date but no assent date
 */
public record Instrument(
        String citation,
        Optional<LocalDate> assentedOn,
        LocalDate inForceFrom,
        String gazetteRef,
        URI sourceUrl,
        VerificationStatus status) {

    public Instrument {
        if (citation == null || citation.isBlank()) {
            throw new IllegalArgumentException("Instrument.citation may not be blank");
        }
        if (inForceFrom == null) {
            throw new IllegalArgumentException(
                    "Instrument.inForceFrom may not be null for \"" + citation + "\"");
        }
        assentedOn = assentedOn == null ? Optional.empty() : assentedOn;
    }

    /** An Act, which has an assent date. */
    public static Instrument assented(String citation, LocalDate assentedOn, LocalDate inForceFrom,
            String gazetteRef, URI sourceUrl, VerificationStatus status) {
        return new Instrument(citation, Optional.of(assentedOn), inForceFrom, gazetteRef, sourceUrl, status);
    }

    /** Subordinate legislation, which is notified rather than assented. */
    public static Instrument notified(String citation, LocalDate inForceFrom,
            String gazetteRef, URI sourceUrl, VerificationStatus status) {
        return new Instrument(citation, Optional.empty(), inForceFrom, gazetteRef, sourceUrl, status);
    }
}
