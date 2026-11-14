package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.repository.Query;

/**
 * An {@code @Query} string must name an encrypted identifier by its Java name (W-253 amendment A): Spring Data encrypts a query's
 * value only for a name it resolves to a property, so {@code 'account_holder'} would match nothing, silently.
 */
class StoredIdentifierQueryTest {

    private static final List<Class<?>> MAIN = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.thiru.wealthlens").stream().map(JavaClass::reflect).toList();

    private static Set<String> storedNamesUnlikeJavaNames() {
        return MAIN.stream().flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> field.isAnnotationPresent(ValueConverter.class)
                        && field.getAnnotation(ValueConverter.class).value() == SearchableIdentifier.class
                        && field.isAnnotationPresent(Field.class))
                .filter(field -> !field.getAnnotation(Field.class).value().equals(field.getName()))
                .map(field -> field.getAnnotation(Field.class).value())
                .collect(Collectors.toSet());
    }

    @Test
    void query_whenItNamesAnEncryptedIdentifier_usesTheJavaName() {
        // Given
        Set<String> storedNames = storedNamesUnlikeJavaNames();

        // When
        List<String> offending = MAIN.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Query.class))
                .flatMap(method -> storedNames.stream().filter(name -> method.getAnnotation(Query.class).value().contains("'" + name + "'"))
                        .map(name -> describe(method) + " names '" + name + "'"))
                .toList();

        // Then — the guard sees the names it guards, and no query uses one
        assertThat(storedNames).contains("account_holder", "demat_account_id", "subject_email");
        assertThat(offending).isEmpty();
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
