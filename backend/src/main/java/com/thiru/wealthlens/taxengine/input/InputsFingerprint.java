package com.thiru.wealthlens.taxengine.input;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A hash of everything that can change the answer.
 *
 * <h2>Why this is derived and not written</h2>
 *
 * <p>{@code 15-review-findings.md} F6 and {@code 23-architecture-consolidation.md} A3. The
 * fingerprint is the input half of the compute cache key, and F6 states the stake exactly:
 * <i>"every input that can change the answer. Omitting one is a wrong cache hit, which is
 * indistinguishable from a wrong computation."</i>
 *
 * <p>F6 then writes that list by hand, and gets it wrong four ways — the list omits the taxpayer's
 * own facts (including the date of birth whose correction it cites as the motivating bug), omits
 * the temporal context, the filing facts and the tax year, and references an
 * {@code electionHistory} that is not one of its parameters, so it does not compile. A list that
 * failed to stay complete <b>inside the document that argues for completeness</b> is the whole
 * case for deriving it instead.
 *
 * <p>So this walks {@link EngineInputs} <b>structurally</b>, by reflection over its record
 * components, all the way down. A3 requires exactly that: <i>"a field added to {@code EngineInputs}
 * is in the key automatically, which is the only way that invariant survives."</i> Today
 * {@link EnteredFacts} is empty and the income side fills it over Phases 5 to 9 — every field
 * added there enters the key without anyone remembering, which is the property that matters most
 * and the one a hand-written list cannot have.
 *
 * <h2>What it does not cover, and why that is right</h2>
 *
 * <p>The rule pack. F6 lists a {@code RulePackRef}; it predates the pack layering.
 * {@code RulePack.computationCacheKey()} already covers the pack side — tax year plus the
 * {@code LAW} and {@code EXEC} layer refs, readable rather than hashed so a cache miss can be
 * diagnosed, and excluding the editorial layers so revisions to the claim catalogue invalidate
 * nothing (G-11). The full key is that, composed with this. Folding the pack in here would
 * duplicate half of it inside an opaque digest and undo G-11.
 *
 * <h2>The canonical form</h2>
 *
 * <ul>
 *   <li>Record components are sorted <b>by name</b>, so reordering a declaration is a refactor and
 *       not a cache invalidation of every stored computation.
 *   <li>{@link Set} and {@link Map} entries are sorted by their own canonical form: both are
 *       unordered by contract, so iteration order must not reach the hash.
 *   <li>{@link List} order <b>is</b> preserved, even where a javadoc says order is not significant.
 *       Sorting could make two genuinely different inputs collide, which is a wrong cache hit;
 *       preserving order can at worst cause a needless miss. Conservative in the direction that
 *       matters.
 *   <li>Every value carries its type, so {@code "5"} and {@code 5} cannot collide.
 * </ul>
 *
 * <p>The walk is reflective throughout, which has a second benefit that was not the reason for it:
 * {@code Method.invoke} returns {@code Object}, so this class never names {@code BigDecimal} and
 * does not trip {@code tax_modules_use_money_not_bigdecimal}. A hand-written canonicaliser that
 * decomposed {@code Money} would.
 *
 * @param sha256 64 lower-case hex digits
 */
public record InputsFingerprint(String sha256) {

    private static final Pattern HEX = Pattern.compile("^[0-9a-f]{64}$");

    private static final String ABSENT = "~";

    public InputsFingerprint {
        if (sha256 == null || !HEX.matcher(sha256).matches()) {
            throw new IllegalArgumentException(
                    "An InputsFingerprint must be 64 lower-case hex digits, but was \"" + sha256
                            + "\"");
        }
    }

    /** The fingerprint of one complete input set. */
    public static InputsFingerprint of(EngineInputs inputs) {
        if (inputs == null) {
            throw new IllegalArgumentException("Cannot fingerprint a null EngineInputs");
        }
        return new InputsFingerprint(sha256Hex(canonicalFormOf(inputs)));
    }

    /**
     * The exact string that was hashed.
     *
     * <p>Exposed because a fingerprint that differs when it should not is otherwise undebuggable:
     * comparing two digests tells you they differ and nothing else, while comparing two canonical
     * forms shows which component moved.
     */
    public static String canonicalFormOf(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append(ABSENT);
            case Enum<?> constant -> out.append("E:").append(constant.name());
            case Optional<?> optional -> {
                out.append("O:");
                append(out, optional.orElse(null));
            }
            case Map<?, ?> map -> appendSorted(out, "M", canonicalEntries(map));
            case Set<?> set -> appendSorted(out, "S", canonicalElements(set));
            case Collection<?> list -> appendOrdered(out, list);
            default -> {
                if (value.getClass().isRecord()) {
                    appendRecord(out, value);
                } else {
                    out.append("V:")
                            .append(value.getClass().getSimpleName())
                            .append(':')
                            .append(value);
                }
            }
        }
    }

    /** Components sorted by name, so a declaration reorder does not invalidate every cache. */
    private static void appendRecord(StringBuilder out, Object record) {
        out.append("T:").append(record.getClass().getSimpleName()).append('{');
        List<RecordComponent> components =
                new ArrayList<>(List.of(record.getClass().getRecordComponents()));
        components.sort(java.util.Comparator.comparing(RecordComponent::getName));

        boolean first = true;
        for (RecordComponent component : components) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(component.getName()).append('=');
            append(out, read(record, component));
        }
        out.append('}');
    }

    private static Object read(Object record, RecordComponent component) {
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Could not read " + record.getClass().getSimpleName() + "." + component.getName()
                            + " while fingerprinting; a component the cache key cannot read is a"
                            + " component the cache key cannot cover",
                    e);
        }
    }

    private static List<String> canonicalEntries(Map<?, ?> map) {
        List<String> entries = new ArrayList<>(map.size());
        map.forEach((k, v) -> entries.add(canonicalFormOf(k) + "=>" + canonicalFormOf(v)));
        return entries;
    }

    private static List<String> canonicalElements(Collection<?> values) {
        List<String> elements = new ArrayList<>(values.size());
        values.forEach(v -> elements.add(canonicalFormOf(v)));
        return elements;
    }

    private static void appendSorted(StringBuilder out, String tag, List<String> parts) {
        parts.sort(java.util.Comparator.naturalOrder());
        out.append(tag).append('[').append(String.join(",", parts)).append(']');
    }

    private static void appendOrdered(StringBuilder out, Collection<?> values) {
        out.append("L[");
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            append(out, value);
        }
        out.append(']');
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
