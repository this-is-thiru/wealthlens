package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/**
 * Reading primitives, where every failure names the field and the pack author is the reader.
 *
 * <p>{@code 19-engineering-foundations.md} E4 makes it a property that the loader throws nothing but
 * {@link PackValidationException}. That is the reason this exists rather than Jackson data binding:
 * Jackson's {@code InvalidTypeIdException} is addressed to a Java developer — it names a base type
 * and a set of subtypes — where the person who needs to act is whoever wrote the JSON. So every
 * method here takes a <em>path</em>, and the message says where in the pack to look.
 *
 * <p>The path is built up by callers as they descend, e.g.
 * {@code law/rates.json rules[2].versions[0].cap.bands[3].rate}.
 */
public final class PackJson {

    private PackJson() {
    }

    /**
     * Builds a domain object, turning its own rejection into a pack error that names the path.
     *
     * <p>{@code 19-engineering-foundations.md} E4 makes it a property that the loader throws nothing
     * but {@link PackValidationException}. Domain constructors throw {@code IllegalArgumentException}
     * — correct for a Java type, and wrong at this boundary: a pack author who leaves
     * {@code whatToCheck} blank should be told <em>which row</em>, not handed an exception addressed
     * to whoever wrote {@code Provenance}.
     *
     * <p>A {@link PackValidationException} whose message already starts with this path passes through
     * untouched, because a nested reader located the failure more precisely than this caller can.
     * One that does not is still prefixed: a domain type may throw a pack error itself — as
     * {@code SlabLadder.validate()} does — and then it knows the defect but not where it lives.
     * The cause is always chained, so a genuine bug in a reader stays diagnosable instead of
     * reading as a pack defect.
     *
     * @param path where in the pack this object lives
     * @param domainConstructor builds it, and validates itself
     */
    public static <T> T construct(String path, java.util.function.Supplier<T> domainConstructor) {
        try {
            return domainConstructor.get();
        } catch (PackValidationException pack) {
            // A nested reader has already located the failure; its path is more precise than this
            // caller's. But a domain type may throw this itself -- SlabLadder.validate() is a
            // load-time guard -- and then it knows the defect and not where the defect lives.
            throw pack.getMessage() != null && pack.getMessage().startsWith(path)
                    ? pack
                    : new PackValidationException(path + ": " + pack.getMessage(), pack);
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            throw new PackValidationException(path + ": " + rejected.getMessage(), rejected);
        }
    }

    /** A required object field. */
    public static JsonNode object(JsonNode parent, String field, String path) {
        JsonNode node = required(parent, field, path);
        if (!node.isObject()) {
            throw malformed(path + "." + field, "an object", node);
        }
        return node;
    }

    /** An optional object field. */
    public static Optional<JsonNode> optionalObject(JsonNode parent, String field, String path) {
        JsonNode node = parent == null ? null : parent.get(field);
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isObject()) {
            throw malformed(path + "." + field, "an object", node);
        }
        return Optional.of(node);
    }

    /** A required array field, mapped element by element. */
    public static <T> List<T> array(
            JsonNode parent, String field, String path, Function<Element, T> each) {
        JsonNode node = required(parent, field, path);
        if (!node.isArray()) {
            throw malformed(path + "." + field, "an array", node);
        }
        List<T> out = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            out.add(each.apply(new Element(node.get(i), path + "." + field + "[" + i + "]")));
        }
        return out;
    }

    /** An array field that may be absent, treated as empty. */
    public static <T> List<T> arrayOrEmpty(
            JsonNode parent, String field, String path, Function<Element, T> each) {
        if (parent == null || parent.get(field) == null || parent.get(field).isNull()) {
            return List.of();
        }
        return array(parent, field, path, each);
    }

    /** A required string. */
    public static String text(JsonNode parent, String field, String path) {
        JsonNode node = required(parent, field, path);
        if (!node.isTextual() || node.asText().isBlank()) {
            throw malformed(path + "." + field, "a non-empty string", node);
        }
        return node.asText();
    }

    /** A string that may be absent or null. */
    public static Optional<String> optionalText(JsonNode parent, String field, String path) {
        JsonNode node = parent == null ? null : parent.get(field);
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw malformed(path + "." + field, "a string", node);
        }
        return Optional.of(node.asText());
    }

    /** A required boolean. */
    public static boolean bool(JsonNode parent, String field, String path) {
        JsonNode node = required(parent, field, path);
        if (!node.isBoolean()) {
            throw malformed(path + "." + field, "true or false", node);
        }
        return node.asBoolean();
    }

    /** A required integer. */
    public static int integer(JsonNode parent, String field, String path) {
        JsonNode node = required(parent, field, path);
        if (!node.isInt()) {
            throw malformed(path + "." + field, "a whole number", node);
        }
        return node.asInt();
    }

    /** A required ISO date. */
    public static LocalDate date(JsonNode parent, String field, String path) {
        String raw = text(parent, field, path);
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw malformed(path + "." + field, "a date as yyyy-MM-dd", raw);
        }
    }

    /** An ISO date that may be absent. */
    public static Optional<LocalDate> optionalDate(JsonNode parent, String field, String path) {
        return optionalText(parent, field, path).map(raw -> {
            try {
                return LocalDate.parse(raw);
            } catch (DateTimeParseException e) {
                throw malformed(path + "." + field, "a date as yyyy-MM-dd", raw);
            }
        });
    }

    /** A required absolute URI. */
    public static URI uri(JsonNode parent, String field, String path) {
        String raw = text(parent, field, path);
        try {
            URI uri = new URI(raw);
            if (!uri.isAbsolute()) {
                throw malformed(path + "." + field, "an absolute URL", raw);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw malformed(path + "." + field, "an absolute URL", raw);
        }
    }

    /** A required enum constant, named in the message when it does not match. */
    public static <E extends Enum<E>> E enumValue(
            JsonNode parent, String field, String path, Class<E> type) {
        String raw = text(parent, field, path);
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        throw malformed(
                path + "." + field,
                "one of " + java.util.Arrays.toString(type.getEnumConstants()),
                raw);
    }

    /**
     * The {@code "type"} discriminator of a tagged union.
     *
     * @param permitted the valid tags, listed in the failure so a pack author sees the options
     */
    public static String tag(JsonNode node, String path, String... permitted) {
        String raw = text(node, "type", path);
        for (String option : permitted) {
            if (option.equals(raw)) {
                return raw;
            }
        }
        throw malformed(path + ".type", "one of " + java.util.Arrays.toString(permitted), raw);
    }

    private static JsonNode required(JsonNode parent, String field, String path) {
        if (parent == null) {
            throw new PackValidationException(path + " is missing, so " + field + " cannot be read");
        }
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new PackValidationException(path + "." + field + " is required and is absent");
        }
        return node;
    }

    private static PackValidationException malformed(String path, String expected, Object actual) {
        return new PackValidationException(
                path + " must be " + expected + ", but was " + describe(actual));
    }

    private static String describe(Object actual) {
        if (actual instanceof JsonNode node) {
            return node.isTextual() ? "\"" + node.asText() + "\"" : node.getNodeType() + " " + node;
        }
        return "\"" + actual + "\"";
    }

    /** One element of an array, with the path that locates it. */
    public record Element(JsonNode node, String path) {}
}
