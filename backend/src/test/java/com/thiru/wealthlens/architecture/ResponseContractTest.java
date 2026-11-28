package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No route answers with a stored document (W-358c2, D-857): what a caller sees is a response type that names its fields, so a field
 * stored for the app — who edited a row, an internal key — never reaches a caller because it was added to the document.
 */
class ResponseContractTest {

    private static final List<Class<?>> CONTROLLERS = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.thiru.wealthlens").stream().map(JavaClass::reflect)
            .filter(type -> type.isAnnotationPresent(RestController.class)).toList();

    @Test
    void handler_whenItAnswers_neverReturnsAStoredDocument() {
        // When
        List<String> offending = CONTROLLERS.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                        .filter(method -> documentsIn(method.getGenericReturnType()).findAny().isPresent())
                        .map(method -> describe(type, method)))
                .toList();

        // Then — the guard sees the controllers it guards
        assertThat(CONTROLLERS).hasSizeGreaterThan(40);
        assertThat(offending).as("handlers answering with an @Entity type, generics included").isEmpty();
    }

    private static Stream<Class<?>> documentsIn(Type type) {
        return switch (type) {
            case Class<?> plain when plain.isArray() -> documentsIn(plain.getComponentType());
            case Class<?> plain -> plain.isAnnotationPresent(Entity.class) ? Stream.of(plain) : Stream.empty();
            case ParameterizedType generic -> Stream.concat(documentsIn(generic.getRawType()),
                    Arrays.stream(generic.getActualTypeArguments()).flatMap(ResponseContractTest::documentsIn));
            case WildcardType wildcard -> Arrays.stream(wildcard.getUpperBounds()).flatMap(ResponseContractTest::documentsIn);
            case GenericArrayType array -> documentsIn(array.getGenericComponentType());
            default -> Stream.empty();
        };
    }

    private static String describe(Class<?> type, Method method) {
        return type.getSimpleName() + "." + method.getName() + " → " + method.getGenericReturnType().getTypeName()
                .replaceAll("[\\w.]+\\.", "");
    }
}
