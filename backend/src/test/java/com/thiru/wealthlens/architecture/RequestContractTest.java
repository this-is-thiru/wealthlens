package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No route reads its body into a stored document (W-206): a request type names what a caller may send, so a field the server owns —
 * an account's billing state, who wrote a card — cannot be written from outside because it was added to the document. The twin of
 * {@link ResponseContractTest}.
 */
class RequestContractTest {

    private static final List<Class<?>> CONTROLLERS = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.thiru.wealthlens").stream().map(JavaClass::reflect)
            .filter(type -> type.isAnnotationPresent(RestController.class)).toList();

    @Test
    void handler_whenItReadsABody_neverBindsAStoredDocument() {
        // When
        List<String> offending = CONTROLLERS.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                        .flatMap(method -> Arrays.stream(method.getParameters())
                                .filter(parameter -> parameter.isAnnotationPresent(RequestBody.class))
                                .filter(parameter -> documentsIn(parameter.getParameterizedType()).findAny().isPresent())
                                .map(parameter -> describe(type, method, parameter))))
                .toList();

        // Then — the guard sees the controllers it guards
        assertThat(CONTROLLERS).hasSizeGreaterThan(40);
        assertThat(offending).as("handlers reading a body into an @Entity type, generics included").isEmpty();
    }

    private static Stream<Class<?>> documentsIn(Type type) {
        return switch (type) {
            case Class<?> plain when plain.isArray() -> documentsIn(plain.getComponentType());
            case Class<?> plain -> plain.isAnnotationPresent(Entity.class) ? Stream.of(plain) : Stream.empty();
            case ParameterizedType generic -> Stream.concat(documentsIn(generic.getRawType()),
                    Arrays.stream(generic.getActualTypeArguments()).flatMap(RequestContractTest::documentsIn));
            case WildcardType wildcard -> Arrays.stream(wildcard.getUpperBounds()).flatMap(RequestContractTest::documentsIn);
            case GenericArrayType array -> documentsIn(array.getGenericComponentType());
            default -> Stream.empty();
        };
    }

    private static String describe(Class<?> type, Method method, Parameter parameter) {
        return type.getSimpleName() + "." + method.getName() + " ← " + parameter.getParameterizedType().getTypeName()
                .replaceAll("[\\w.]+\\.", "");
    }
}
