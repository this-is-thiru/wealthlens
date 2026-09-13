package com.thiru.wealthlens.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The conventions documented in CLAUDE.md, made executable.
 *
 * <p>These previously relied on review to enforce. Spring Modulith already verifies module
 * boundaries ({@code WealthLensModulithTest}); this covers the layering and injection rules
 * it does not.
 */
@AnalyzeClasses(
        packages = "com.thiru.wealthlens",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule no_field_injection =
            noFields()
                    .should()
                    .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .because("CLAUDE.md: DI is constructor-based via @RequiredArgsConstructor; injected fields are private final");

    @ArchTest
    static final ArchRule controllers_do_not_touch_repositories =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("CLAUDE.md: controllers carry no business logic; they delegate to services");

    @ArchTest
    static final ArchRule repositories_are_interfaces =
            classes()
                    .that().resideInAPackage("..repository..")
                    .and().haveSimpleNameEndingWith("Repository")
                    .should().beInterfaces()
                    .because("repositories are Spring Data derived-query interfaces, never hand-written classes");

    @ArchTest
    static final ArchRule controllers_are_named_and_annotated_consistently =
            classes()
                    .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should().haveSimpleNameEndingWith("Controller")
                    .andShould().resideInAPackage("..controller..")
                    .because("REST entry points are discoverable by name and location");

    @ArchTest
    static final ArchRule services_do_not_live_in_other_layers =
            noClasses()
                    .that().areAnnotatedWith("org.springframework.stereotype.Service")
                    .should().resideInAnyPackage("..controller..", "..repository..", "..entity..", "..dto..")
                    .because("CLAUDE.md layering: business logic never lives in the controller, "
                            + "repository or model layers. Modules may name their business packages "
                            + "freely (taxplanning uses engine/, recommendation/, document/) — what "
                            + "matters is that a @Service is not hiding inside another layer");

    @ArchTest
    static final ArchRule entities_are_not_exposed_from_repositories_upward =
            noClasses()
                    .that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .because("dependencies point downward through the layers");

    @ArchTest
    static final ArchRule no_console_printing =
            noClasses()
                    .should().accessField(System.class, "out")
                    .orShould().accessField(System.class, "err")
                    .because("CLAUDE.md: logging goes through Log4j2 (@Log4j2), never the console")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule mongo_fields_are_snake_case =
            fields()
                    .that().areAnnotatedWith("org.springframework.data.mongodb.core.mapping.Field")
                    .should(SnakeCaseFieldCondition.snakeCase())
                    .because("CLAUDE.md: MongoDB collections and fields are snake_case");
}
