package com.thiru.wealthlens.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

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

    /**
     * Money rounding lives in one place (TL-8).
     *
     * <p>This does <b>not</b> enforce that every money write is canonicalised — ArchUnit reads
     * structure, not arithmetic, so no rule can see a missing {@code TMoney.add}. Behavioural tests
     * cover that. What it does catch is the failure that actually happened: the same HALF_UP paise
     * rounding was independently reimplemented three times, in {@code ChargeSummaryReport},
     * {@code ChargeReconciliationService} and again in {@code TMoney}. Three copies of a rounding
     * rule is how two of them eventually disagree.
     *
     * <p>{@code ChargeRounding} is exempt and is not a duplicate: it is the engine's configurable
     * per-rule policy, which also does CEILING and rupee scale, and is a different thing from
     * canonicalising a stored amount.
     */
    @ArchTest
    static final ArchRule money_rounding_lives_in_one_place =
            noClasses()
                    .that().resideOutsideOfPackage("..shared.util.money..")
                    .and().resideOutsideOfPackage("..taxengine.money..")
                    .and().haveSimpleNameNotEndingWith("ChargeRounding")
                    .should().callMethod(java.math.BigDecimal.class, "setScale", int.class, java.math.RoundingMode.class)
                    .because("TL-8: paise rounding belongs to TMoney; a second copy is how two roundings diverge."
                            + " taxengine.money is the second permitted home, not a third copy: TL-8 section 7 names"
                            + " 'anything filed on someone else's behalf' as the trigger for exact decimal storage,"
                            + " and taxplanning ships ITR-ready figures. Its rounding likewise lives in one package.");

    /**
     * The two money representations may never meet outside one named seam.
     *
     * <p>{@code TMoney} is canonicalised {@code double} (TL-8 Option B); {@code taxengine} is exact
     * decimal (TL-8 section 7). Both are correct for their own module and mixing them silently
     * discards the guarantee of whichever one loses. Conversion happens in {@code MoneyBridge},
     * inbound only, and nowhere else.
     *
     * <p>Vacuous until the engine has types — which is the point: the guard is in place before the
     * first {@code Money} is written, not after.
     */
    @ArchTest
    static final ArchRule taxengine_does_not_use_TMoney =
            noClasses()
                    .that().resideInAPackage("..taxengine..")
                    .should().accessClassesThat().haveFullyQualifiedName("com.thiru.wealthlens.shared.util.money.TMoney")
                    .because("taxengine is exact-decimal; TMoney is canonicalised double."
                            + " Conversion belongs to MoneyBridge in taxplanning.portfolio, and to nothing else");

    /**
     * Only the portfolio adapter converts a canonicalised {@code double} into an exact {@code Money}.
     *
     * <p>Matched by method name rather than by {@code MoneyBridge.class}: the class does not exist
     * yet, and a rule that cannot be written until the thing it guards exists is a rule that arrives
     * after the mistake. Expressed as {@code noClasses} so the input set is every class outside the
     * adapter package — ArchUnit 1.3 fails a rule whose {@code that()} matches nothing, and no class
     * calls this method today.
     */
    @ArchTest
    static final ArchRule only_the_adapter_converts =
            noClasses()
                    .that().resideOutsideOfPackage("..taxplanning.portfolio..")
                    .should().callMethodWhere(target(name("fromTMoney")))
                    .because("one seam, named and testable, rather than a conversion scattered per call site");

    /**
     * The engine fetches nothing.
     *
     * <p>Everything a computation may read arrives in {@code EngineInputs}. This is not tidiness:
     * the optimiser runs thousands of evaluations inside one request, and a single repository call
     * inside a node turns those into thousands of database round trips. It is also what makes a
     * computation reproducible years later — a result that re-read the database would not be.
     */
    @ArchTest
    static final ArchRule taxengine_fetches_nothing =
            noClasses()
                    .that().resideInAPackage("..taxengine..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..repository..", "org.springframework.data..", "..taxplanning..")
                    .because("everything the engine may read arrives in EngineInputs; it fetches nothing");

    /**
     * The engine reads no clock.
     *
     * <p>The instant of evaluation is an input, not ambient state. A clock inside makes a golden
     * test time-dependent and an audit irreproducible — and the design has rules keyed on filing
     * date and on an asset's acquisition date, which must be supplied, never inferred from today.
     */
    @ArchTest
    static final ArchRule taxengine_has_no_clock =
            noClasses()
                    .that().resideInAPackage("..taxengine..")
                    .should().callMethodWhere(target(name("now")))
                    .orShould().dependOnClassesThat().haveFullyQualifiedName("java.time.Clock")
                    .because("asOf is an EngineInputs field; a clock inside the engine makes golden tests"
                            + " time-dependent and audits irreproducible");

    /**
     * The engine never swallows a failure.
     *
     * <p>ArchUnit cannot see a {@code catch} block, so this forbids the dependency instead: a class
     * that neither catches, declares nor constructs a raw {@code Exception} cannot swallow one. The
     * engine throws its own sealed {@code TaxEngineException} hierarchy, so raw {@code Exception}
     * has no legitimate use here.
     *
     * <p>The failure this prevents is the one the placeholder actually had: a rule that could not be
     * evaluated, caught, and silently replaced with zero — so a deduction vanished and the user
     * filed on the result.
     */
    @ArchTest
    static final ArchRule taxengine_does_not_swallow_failures =
            noClasses()
                    .that().resideInAPackage("..taxengine..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.Exception")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.RuntimeException")
                    .because("a swallowed failure becomes a silent zero; the engine throws TaxEngineException");

    /**
     * Money is {@code Money}, and rates are {@code Rate}.
     *
     * <p>Expressed as a dependency rule rather than a field rule because {@code taxengine} has no
     * fields yet, and ArchUnit 1.3 fails a rule that checks nothing. It is also stronger: it catches
     * a {@code BigDecimal} parameter, return type or local, not only a field.
     */
    @ArchTest
    static final ArchRule taxengine_uses_money_not_bigdecimal =
            noClasses()
                    .that().resideInAPackage("..taxengine..")
                    .and().resideOutsideOfPackage("..taxengine.money..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("java.math.BigDecimal")
                    .because("D-MONEY: money is Money, rates are Rate, and nothing else is either");

    /**
     * {@code ComputationStep} was withdrawn in favour of the DAG's {@code ComputationNode}.
     *
     * <p>Not an ArchUnit rule: ArchUnit reads bytecode, and this is a name that must not appear in
     * source at all. It is a gate because the withdrawal was already missed once — the name survived
     * in ten design documents after being replaced, which is the same two-vocabularies failure this
     * whole design was written to avoid.
     */
    @Test
    void withdrawn_abstraction_names_do_not_appear_in_source() throws IOException {
        // Split so this test does not match itself.
        // Split so the literal does not match this file's own body.
        String withdrawn = "Computation" + "Step";
        Path root = Path.of("src");
        try (Stream<Path> paths = Files.walk(root)) {
            List<String> offenders = paths
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    // This file is exempt: it names the withdrawn abstraction in order to forbid it,
                    // the same shape as ChargeRounding's exemption from the money rule above. The
                    // first run of this gate caught exactly this file, which is how we know it bites.
                    .filter(f -> !f.endsWith("ArchitectureTest.java"))
                    .filter(f -> {
                        try {
                            return Files.readString(f).contains(withdrawn);
                        } catch (IOException e) {
                            throw new IllegalStateException("could not read " + f, e);
                        }
                    })
                    .map(Path::toString)
                    .toList();
            if (!offenders.isEmpty()) {
                throw new AssertionError(
                        withdrawn + " was withdrawn in favour of ComputationNode (23-architecture-consolidation.md A4),"
                                + " but still appears in: " + offenders);
            }
        }
    }
}
