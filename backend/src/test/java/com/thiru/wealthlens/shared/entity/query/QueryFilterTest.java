package com.thiru.wealthlens.shared.entity.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryFilterTest {

    private static List<QueryFilter> filterOn(String key, QueryFilter.FilterOperation operation) {
        return new ArrayList<>(List.of(QueryFilter.builder().filterKey(key).operation(operation).value("Lakshmi").build()));
    }

    @Test
    void validateAndSanitizationOfQueryFilters_whenTheAccountHolderIsMatchedByPrefix_keepsItsName() {
        // Given — stored plain since W-428a, so a prefix finds what it names (W-428c, D-1039)
        List<QueryFilter> filters = filterOn("account_holder", QueryFilter.FilterOperation.STARTS_WITH);

        // When
        QueryFilter.validateAndSanitizationOfQueryFilters(filters, UserMail.from("holder@example.com"));

        // Then — QueryBuilder turns the stored name into the property, as for every other key
        assertThat(filters).extracting(QueryFilter::getFilterKey).containsExactlyInAnyOrder("account_holder", "email");
    }

    @Test
    void validateAndSanitizationOfQueryFilters_whenAPlainFieldIsMatchedByPrefix_keepsItsName() {
        // Given
        List<QueryFilter> filters = filterOn("stock_code", QueryFilter.FilterOperation.STARTS_WITH);

        // When
        QueryFilter.validateAndSanitizationOfQueryFilters(filters, UserMail.from("holder@example.com"));

        // Then
        assertThat(filters).extracting(QueryFilter::getFilterKey).containsExactlyInAnyOrder("stock_code", "email");
    }
}
