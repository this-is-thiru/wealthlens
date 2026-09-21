package com.thiru.wealthlens.taxengine.result;

import com.thiru.wealthlens.taxrules.id.TaxYearId;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The short, shareable reference for one computation — {@code TXC-2026-9F3A21}.
 *
 * <p>{@code 19-engineering-foundations.md} E6. Printed on the PDF, returned in every API response,
 * and included in every error body. Its value is entirely practical: <i>"my tax looks wrong"</i> is
 * unanswerable, while <i>"my tax looks wrong, receipt TXC-2026-9F3A21"</i> resolves server-side to
 * the pack version and checksum, the inputs fingerprint, the regime, the portfolio {@code asOf},
 * the draft revision and the full trace.
 *
 * <p>It becomes the <b>only</b> way to answer the question once {@code 13-edge-cases.md} R-8's
 * retrospective amendments mean the same inputs legitimately give two different answers — without a
 * receipt there is no way to say which of them someone is looking at.
 *
 * <p>Derived from the {@link ComputationId}, never stored separately: one id has one receipt, and
 * a receipt that could drift from its computation would be worse than none.
 *
 * @param value {@code TXC-<taxYear>-<6 base32 characters>}
 */
public record ComputationReceipt(String value) {

    private static final Pattern FORM = Pattern.compile("^TXC-\\d{4}-[A-Z2-7]{6}$");

    /** RFC 4648 base32, which omits the characters that are misread aloud: 0, 1, 8 and 9. */
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static final int LENGTH = 6;

    public ComputationReceipt {
        if (value == null || !FORM.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "A ComputationReceipt must read TXC-<year>-<6 base32 characters>, but was \""
                            + value + "\"");
        }
    }

    /** The receipt for one computation in one year. */
    public static ComputationReceipt of(ComputationId id, TaxYearId taxYear) {
        if (id == null || taxYear == null) {
            throw new IllegalArgumentException(
                    "A ComputationReceipt is derived from a ComputationId and a tax year; both are"
                            + " required");
        }
        return new ComputationReceipt(
                "TXC-" + taxYear.startYear() + "-" + base32Of(id));
    }

    /**
     * Six base32 characters taken from the id's own hex digits.
     *
     * <p>Not a hash: the id is already non-guessable, so this only has to shorten it. Deriving
     * rather than generating is what makes the mapping stable — the same id always prints the same
     * receipt, on the PDF and in the error body alike.
     */
    private static String base32Of(ComputationId id) {
        String hex = id.value().replace("-", "");
        StringBuilder out = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            int chunk = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            out.append(BASE32.charAt(chunk % BASE32.length()));
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
