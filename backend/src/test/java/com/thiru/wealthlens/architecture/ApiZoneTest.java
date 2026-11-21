package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.shared.web.AdminApi;
import com.thiru.wealthlens.shared.web.MeApi;
import com.thiru.wealthlens.shared.web.PublicApi;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The zones are declared, never spelled (D-855): each controller carries exactly one zone annotation, none writes the `/api` prefix
 * that `ApiZones` adds, and no `/me` handler takes a user from its path.
 */
class ApiZoneTest {

    private static final java.util.Set<String> CRUD_VERBS = java.util.Set.of("add", "delete", "update", "all", "list", "get");

    private static final List<Class<?>> CONTROLLERS = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.thiru.wealthlens").stream().map(JavaClass::reflect)
            .filter(type -> type.isAnnotationPresent(RestController.class)).toList();

    @Test
    void controller_whenDeclared_carriesExactlyOneZone() {
        // When
        List<String> offending = CONTROLLERS.stream().filter(type -> Stream.of(PublicApi.class, MeApi.class, AdminApi.class)
                .filter(type::isAnnotationPresent).count() != 1).map(Class::getSimpleName).toList();

        // Then — the guard sees the controllers it guards
        assertThat(CONTROLLERS).hasSizeGreaterThan(40);
        assertThat(offending).as("controllers without exactly one of @PublicApi, @MeApi, @AdminApi").isEmpty();
    }

    @Test
    void mapping_whenDeclared_neverSpellsTheApiPrefix() {
        // When
        List<String> offending = CONTROLLERS.stream().flatMap(type -> Stream.concat(Stream.of(type), Arrays.stream(type.getDeclaredMethods()))
                .filter(element -> AnnotatedElementUtils.hasAnnotation(element, RequestMapping.class))
                .filter(element -> Arrays.stream(AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class).path())
                        .anyMatch(path -> path.startsWith("/api")))
                .map(element -> type.getSimpleName() + " " + element)).toList();

        // Then
        assertThat(offending).as("mappings that write /api themselves").isEmpty();
    }

    @Test
    void mapping_whenDeclared_namesNoCrudVerb() {
        // When — a path names a thing; its method says what is done to it (W-358c3a, D-859 C2a)
        List<String> offending = CONTROLLERS.stream().flatMap(type -> Stream.concat(Stream.of(type), Arrays.stream(type.getDeclaredMethods()))
                .filter(element -> AnnotatedElementUtils.hasAnnotation(element, RequestMapping.class))
                .flatMap(element -> Arrays.stream(AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class).path())
                        .filter(path -> Arrays.stream(path.split("/")).anyMatch(CRUD_VERBS::contains))
                        .map(path -> type.getSimpleName() + " " + path))).toList();

        // Then
        assertThat(offending).as("mappings that write add, delete, update, all, list or get into the path").isEmpty();
    }

    @Test
    void meHandler_whenDeclared_takesNoEmailFromItsPath() {
        // When
        List<String> offending = CONTROLLERS.stream().filter(type -> type.isAnnotationPresent(MeApi.class))
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()).filter(method -> takesEmailFromThePath(method))
                        .map(method -> type.getSimpleName() + "." + method.getName())).toList();

        // Then — the user of a /me route is the token's (@CallerEmail, D-855 C4a)
        assertThat(offending).isEmpty();
    }

    private static boolean takesEmailFromThePath(Method method) {
        for (Parameter parameter : method.getParameters()) {
            PathVariable variable = parameter.getAnnotation(PathVariable.class);
            if (variable != null && Stream.of(variable.value(), variable.name(), parameter.getName()).anyMatch("email"::equals)) {
                return true;
            }
        }
        return false;
    }
}
