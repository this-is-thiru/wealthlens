package com.thiru.wealthlens.shared.entity.query;

import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Getter
public class QueryBuilder {

    private static final Object NULL = null;

    Set<Criteria> criteriaSet;

    public QueryBuilder() {
        this.criteriaSet = new HashSet<>();
    }

    public void addAsCriteria(QueryFilter queryFilter) {
        sanitize(queryFilter);

        QueryFilter.FilterOperation operation = queryFilter.getOperation();
        String filterKey = queryFilter.getFilterKey();

        Criteria criteria = TCollectionUtil.findFirst(criteriaSet, queryCriteria -> filterKey.equals(queryCriteria.getKey()));

        boolean isNewCriteria = false;
        if (criteria == null) {
            criteria = new Criteria(filterKey);
            isNewCriteria = true;
        }

        switch (operation) {
            case EQUALS -> criteria.is(queryFilter.getValue());
            case NOT_EQUALS -> criteria.ne(queryFilter.getValue());
            case GREATER_THAN -> criteria.gt(queryFilter.getValue());
            case LESSER_THAN -> criteria.lt(queryFilter.getValue());
            case GREATER_THAN_OR_EQUAL_TO -> criteria.gte(queryFilter.getValue());
            case LESSER_THAN_OR_EQUAL_TO -> criteria.lte(queryFilter.getValue());
            case STARTS_WITH -> criteria.regex(queryFilter.getValue().toString());
            case CONTAINS -> criteria.in(queryFilter.getValues());
            case IS_NULL -> criteria.is(NULL);
            case IS_NOT_NULL -> criteria.ne(NULL);
        }

        if (isNewCriteria) {
            criteriaSet.add(criteria);
        }
    }

    public Query build() {
        Query query = new Query();
        criteriaSet.forEach(query::addCriteria);
        return query;
    }

    private static void sanitize(QueryFilter queryFilter) {
        if (queryFilter.getIsDateField()) {
            String value = (String) queryFilter.getValue();
            queryFilter.setValue(TLocalDate.convertToDate(value));
        }
    }
}
