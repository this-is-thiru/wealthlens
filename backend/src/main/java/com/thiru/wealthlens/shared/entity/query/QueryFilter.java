package com.thiru.wealthlens.shared.entity.query;

import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryFilter {

    private String filterKey;
    private FilterOperation operation;
    private Object value;
    private List<Object> values;
    private LogicalOperation logicalOperation;
    private LogicalOperation expressionLogicalOperation;
    private Boolean allowEmptyOrNull;
    private Boolean caseSensitive;
    private Boolean isDateField;

    public enum FilterOperation {
        EQUALS, NOT_EQUALS, STARTS_WITH, CONTAINS, GREATER_THAN, LESSER_THAN, GREATER_THAN_OR_EQUAL_TO, LESSER_THAN_OR_EQUAL_TO, IS_NULL, IS_NOT_NULL
    }

    public enum LogicalOperation {
        AND, OR
    }

    /**
     * Stored encrypted (W-253), each to its Java name: only an exact match can find a value, so an ordering or a prefix is refused
     * rather than left to match nothing, and the query names the property, since Spring Data encrypts a query's value only for a
     * name it resolves to one.
     */
    private static final Map<String, String> ENCRYPTED_FIELDS = Map.of("account_holder", "accountHolder");
    private static final Set<FilterOperation> EXACT_MATCHES = EnumSet.of(FilterOperation.EQUALS, FilterOperation.NOT_EQUALS,
            FilterOperation.CONTAINS, FilterOperation.IS_NULL, FilterOperation.IS_NOT_NULL);

    public static void validateAndSanitizationOfQueryFilters(List<QueryFilter> queryFilters, UserMail userMail) {

        sanitizeAndPopulateEmailFilter(queryFilters, userMail.getEmail());
        validateFilters(queryFilters);
        queryFilters.forEach(filter -> filter.setFilterKey(ENCRYPTED_FIELDS.getOrDefault(filter.getFilterKey(), filter.getFilterKey())));
    }

    private static void sanitizeAndPopulateEmailFilter(List<QueryFilter> queryFilters, String email) {

        List<QueryFilter> sanitizedQueryFilters = TCollectionUtil.filter(queryFilters, filter -> !AssetEntity.EMAIL.equals(filter.getFilterKey()));
        QueryFilter emailFilter = QueryFilter.builder().filterKey("email").operation(QueryFilter.FilterOperation.EQUALS).value(email).isDateField(false).build();
        sanitizedQueryFilters.add(emailFilter);

        queryFilters.clear();
        queryFilters.addAll(sanitizedQueryFilters);
    }

    private static void validateFilters(List<QueryFilter> queryFilters) {

        List<QueryFilter> invalidQueryFilters = TCollectionUtil.filter(queryFilters,
                filter -> !AssetEntity.ALLOWED_FIELDS.contains(filter.getFilterKey()));

        List<String> invalidFieldsForFilter = TCollectionUtil.map(invalidQueryFilters, QueryFilter::getFilterKey);

        if (!invalidFieldsForFilter.isEmpty()) {
            throw new BadRequestException("These fields are not allowed for filtering: " + invalidFieldsForFilter);
        }

        List<String> inexactOnEncrypted = TCollectionUtil.map(TCollectionUtil.filter(queryFilters,
                filter -> ENCRYPTED_FIELDS.containsKey(filter.getFilterKey()) && !EXACT_MATCHES.contains(filter.getOperation())),
                filter -> filter.getFilterKey() + " " + filter.getOperation());
        if (!inexactOnEncrypted.isEmpty()) {
            throw new BadRequestException("These fields are stored encrypted and can only be matched exactly: " + inexactOnEncrypted);
        }
    }
}
