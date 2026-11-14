package com.thiru.wealthlens.shared.entity.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryFilterTest {

    private static List<QueryFilter> filterOn(String key, QueryFilter.FilterOperation operation) {
        return new ArrayList<>(List.of(QueryFilter.builder().filterKey(key).operation(operation).value("Lakshmi").build()));
    }

    @Test
    void validateAndSanitizationOfQueryFilters_whenAnEncryptedFieldIsMatchedExactly_queriesItByItsJavaName() {
        // Given
        List<QueryFilter> filters = filterOn("account_holder", QueryFilter.FilterOperation.EQUALS);

        // When
        QueryFilter.validateAndSanitizationOfQueryFilters(filters, UserMail.from("holder@example.com"));

        // Then — Spring Data encrypts the value only for a name it resolves to the property (W-253 amendment A)
        assertThat(filters).extracting(QueryFilter::getFilterKey).containsExactlyInAnyOrder("accountHolder", "email");
    }

    @Test
    void validateAndSanitizationOfQueryFilters_whenAnEncryptedFieldIsMatchedByPrefix_refuses() {
        // Given
        List<QueryFilter> filters = filterOn("account_holder", QueryFilter.FilterOperation.STARTS_WITH);

        // When / Then
        assertThatThrownBy(() -> QueryFilter.validateAndSanitizationOfQueryFilters(filters, UserMail.from("holder@example.com")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("These fields are stored encrypted and can only be matched exactly: [account_holder STARTS_WITH]");
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
