package com.thiru.wealthlens.shared.migration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import org.bson.types.Binary;
import org.bson.types.ObjectId;

/**
 * A stored MongoDB value as the PostgreSQL column of the given type takes it (W-337g, D-953 C1a), and the one text form both
 * sides are compared in. Nothing is decrypted or re-encrypted: ciphertext is text and stays as it is. A value a column
 * cannot hold exactly is refused, never rounded or truncated.
 */
final class ColumnValue {

    private ColumnValue() {
    }

    /**
     * @param forms how the source is read: its time zone is the one a stored {@code LocalDateTime} is read in ({@code m1.6.1}
     *     stored one through Spring Data's default converter, which takes it as a time in the writing JVM's zone;
     *     {@link RowMapping} picks the zone that gives UTC); a {@code LocalDate} is read as {@link FormerForms#date} reads it,
     *     and an {@code Instant} is zone-free
     * @throws IllegalStateException if the column cannot hold the value exactly
     */
    static Object of(Object value, String columnType, FormerForms forms) {
        return switch (columnType) {
            case "text", "character varying" -> text(value);
            case "integer" -> require(value, Integer.class, columnType);
            case "bigint" -> widened(value, columnType);
            case "double precision" -> fractional(value, columnType);
            case "boolean" -> require(value, Boolean.class, columnType);
            case "date" -> forms.date(require(value, Date.class, columnType));
            case "timestamp without time zone" -> LocalDateTime.ofInstant(require(value, Date.class, columnType).toInstant(),
                    forms.timeZone());
            case "timestamp with time zone" -> OffsetDateTime.ofInstant(require(value, Date.class, columnType).toInstant(), ZoneOffset.UTC);
            case "bytea" -> require(value, Binary.class, columnType).getData();
            default -> throw new IllegalStateException("The copier stores no value in a " + columnType + " column");
        };
    }

    /** The text a value is compared in, the same whether it was mapped from MongoDB or read back from PostgreSQL. */
    static String canonical(Object value) {
        return switch (value) {
            case null -> "null";
            case Number number -> new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            case OffsetDateTime instant -> instant.toInstant().toString();
            case byte[] bytes -> Base64.getEncoder().encodeToString(bytes);
            default -> value.toString();
        };
    }

    private static String text(Object value) {
        if (value instanceof ObjectId id) {
            return id.toHexString();
        }
        return require(value, String.class, "text");
    }

    /** A {@code long} field holding a small value may have been stored as a 32-bit integer; widening it is exact. */
    private static Long widened(Object value, String columnType) {
        return value instanceof Integer whole ? Long.valueOf(whole) : require(value, Long.class, columnType);
    }

    /** A whole number written into a {@code double} field by hand is stored as an integer; every {@code int} is an exact double. */
    private static Double fractional(Object value, String columnType) {
        return value instanceof Integer whole ? Double.valueOf(whole) : require(value, Double.class, columnType);
    }

    private static <T> T require(Object value, Class<T> type, String columnType) {
        if (!type.isInstance(value)) {
            throw new IllegalStateException("A " + value.getClass().getSimpleName() + " cannot be stored in a " + columnType + " column");
        }
        return type.cast(value);
    }
}
