package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.shared.protection.SearchableColumn;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * A native query must not name a searchable column (W-253 amendment A, carried to JPA by W-337f): JPA puts a parameter
 * through {@link SearchableColumn} only for a JPQL or derived query, where it resolves the property, so a native
 * {@code WHERE email = :email} compares the plain value with the stored ciphertext and matches nothing, silently.
 */
class StoredIdentifierQueryTest {

    private static final List<Class<?>> MAIN = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.thiru.wealthlens").stream().map(JavaClass::reflect).toList();

    private static Set<String> searchableColumnNames() {
        return MAIN.stream().flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> field.isAnnotationPresent(Convert.class)
                        && field.getAnnotation(Convert.class).converter() == SearchableColumn.class)
                .map(StoredIdentifierQueryTest::columnName)
                .collect(Collectors.toSet());
    }

    /** The name Hibernate gives the column: the one {@code @Column} states, or Spring Boot's snake_case of the field name. */
    private static String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isEmpty()) {
            return column.name();
        }
        return field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    @Test
    void nativeQuery_whenItNamesASearchableColumn_isReported() {
        // Given
        Set<String> columns = searchableColumnNames();

        // When
        List<String> offending = MAIN.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Query.class) && method.getAnnotation(Query.class).nativeQuery())
                .flatMap(method -> columns.stream()
                        .filter(column -> Pattern.compile("\\b" + column + "\\b").matcher(method.getAnnotation(Query.class).value()).find())
                        .map(column -> describe(method) + " names '" + column + "'"))
                .toList();

        // Then — the guard sees the columns it guards (each in db/migration's DDL), and no native query names one
        assertThat(columns).contains("email", "account_holder", "demat_account_id", "subject_email", "changed_by", "confirmed_by",
                "created_by");
        assertThat(offending).isEmpty();
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
