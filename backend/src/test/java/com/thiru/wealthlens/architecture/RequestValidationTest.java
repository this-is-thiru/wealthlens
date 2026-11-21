package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every request body is validated before its handler runs (W-358d4, D-886): a body missing a field its type requires is refused as a
 * 400 naming the field, the same way on every route, instead of reaching a service that crashes on it or stores it empty.
 */
class RequestValidationTest {

    private static final List<Class<?>> CONTROLLERS = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.thiru.wealthlens").stream().map(JavaClass::reflect)
            .filter(type -> type.isAnnotationPresent(RestController.class)).toList();

    @Test
    void handler_whenItReadsABody_validatesIt() {
        // When
        List<String> unvalidated = CONTROLLERS.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                        .flatMap(method -> Arrays.stream(method.getParameters())
                                .filter(parameter -> parameter.isAnnotationPresent(RequestBody.class))
                                .filter(parameter -> !parameter.isAnnotationPresent(Valid.class))
                                .map(parameter -> describe(type, method, parameter))))
                .toList();

        // Then — the guard sees the controllers it guards
        assertThat(CONTROLLERS).hasSizeGreaterThan(40);
        assertThat(unvalidated).as("@RequestBody parameters without @Valid").isEmpty();
    }

    private static String describe(Class<?> type, Method method, Parameter parameter) {
        return type.getSimpleName() + "." + method.getName() + "(" + parameter.getType().getSimpleName() + ")";
    }
}
