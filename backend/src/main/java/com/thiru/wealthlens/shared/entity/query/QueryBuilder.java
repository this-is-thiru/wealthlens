package com.thiru.wealthlens.shared.entity.query;

import com.thiru.wealthlens.shared.util.time.TLocalDate;
import jakarta.persistence.criteria.Path;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds a {@link Specification} from a caller's ad-hoc {@link QueryFilter} list (W-337e, D-949 C6a — replaces the
 * MongoDB {@code Criteria} version this class held before the port).
 *
 * <p>Every filter's key is the Java property name on the target entity (not the stored column name), the same way
 * a JPQL {@code WHERE} clause always is — Hibernate applies an attribute's own {@code @Convert} (e.g.
 * {@code SearchableColumn} on {@code email} / {@code accountHolder}) when a predicate is built against
 * {@code root.get(propertyName)}, exactly as it already does for the hand-written JPQL queries elsewhere in this
 * codebase. {@link QueryFilter#getFilterKey()} is sanitised to the Java property name before this class sees it.
 */
public final class QueryBuilder {

    /**
     * {@code QueryFilter}'s keys are the stored field's own name (snake_case, same as the old Mongo document key)
     * for every field except the one Spring Data's converter needed to see by Java name to encrypt the query value
     * correctly — the same special case {@code SearchableIdentifier}'s javadoc describes. A JPQL-style
     * {@link Specification} always needs the Java property name, so every key is translated here.
     */
    private static final Map<String, String> JAVA_PROPERTY_NAMES = Map.ofEntries(
            Map.entry("email", "email"),
            Map.entry("transaction_date", "transactionDate"),
            Map.entry("transaction_type", "transactionType"),
            Map.entry("account_type", "accountType"),
            Map.entry("account_holder", "accountHolder"),
            Map.entry("exchange_name", "exchangeName"),
            Map.entry("stock_code", "stockCode"),
            Map.entry("broker_name", "brokerName"),
            Map.entry("asset_type", "assetType")
    );

    private QueryBuilder() {
    }

    public static <T> Specification<T> build(List<QueryFilter> queryFilters) {
        return Specification.allOf(queryFilters.stream().map(QueryBuilder::<T>toSpecification).toList());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Specification<T> toSpecification(QueryFilter queryFilter) {
        sanitize(queryFilter);
        String attribute = JAVA_PROPERTY_NAMES.getOrDefault(queryFilter.getFilterKey(), queryFilter.getFilterKey());
        Object value = queryFilter.getValue();

        return (root, query, cb) -> {
            Path path = root.get(attribute);
            return switch (queryFilter.getOperation()) {
                case EQUALS -> cb.equal(path, value);
                case NOT_EQUALS -> cb.notEqual(path, value);
                case GREATER_THAN -> cb.greaterThan(path, (Comparable) value);
                case LESSER_THAN -> cb.lessThan(path, (Comparable) value);
                case GREATER_THAN_OR_EQUAL_TO -> cb.greaterThanOrEqualTo(path, (Comparable) value);
                case LESSER_THAN_OR_EQUAL_TO -> cb.lessThanOrEqualTo(path, (Comparable) value);
                case STARTS_WITH -> cb.like((Path<String>) path, value + "%");
                case CONTAINS -> path.in(queryFilter.getValues());
                case IS_NULL -> cb.isNull(path);
                case IS_NOT_NULL -> cb.isNotNull(path);
            };
        };
    }

    // Preserves the original's exact null-unboxing: a filter that omits isDateField throws a NullPointerException,
    // same as the Mongo-era QueryBuilder did (MongoTemplateIntegrationTest#getDocuments_whenMongoDown_shouldThrow).
    private static void sanitize(QueryFilter queryFilter) {
        if (queryFilter.getIsDateField()) {
            String value = (String) queryFilter.getValue();
            try {
                queryFilter.setValue(TLocalDate.convertToDate(value));
            } catch (DateTimeParseException e) {
                // The caller's value, so a 400 naming it (W-415), not the parser's 500
                throw new IllegalArgumentException("A date filter's value must be a date as yyyy-MM-dd; got '" + value + "' for "
                        + queryFilter.getFilterKey(), e);
            }
        }
    }
}
