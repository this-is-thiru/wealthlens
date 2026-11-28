package com.thiru.wealthlens.shared.entity.query;

import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import java.util.List;
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

    public static void validateAndSanitizationOfQueryFilters(List<QueryFilter> queryFilters, UserMail userMail) {

        sanitizeAndPopulateEmailFilter(queryFilters, userMail.getEmail());
        validateFilters(queryFilters);
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
    }
}
