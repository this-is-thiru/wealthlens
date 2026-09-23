package com.thiru.wealthlens.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
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

    private static final Set<String> GENERIC_NAMES_BEFORE_W045A = Set.of(
            "AuthHelper", "FileHelper", "ParserUtil", "DoubleUtil",
            "ChargeRecordingGatewayImpl", "UserDetailsServiceImpl", "UserDetailsImpl",
            "AssetRequestParserImpl");

    private static final DescribedPredicate<JavaClass> GRANDFATHERED_GENERIC_NAMES =
            DescribedPredicate.describe(
                    "named before W-045a " + GENERIC_NAMES_BEFORE_W045A,
                    javaClass -> GENERIC_NAMES_BEFORE_W045A.contains(javaClass.getSimpleName()));

    private static final DescribedPredicate<JavaClass> SHARED_T_PREFIXED_UTILITIES =
            DescribedPredicate.describe(
                    "a T-prefixed utility in shared.util (CLAUDE.md, Conventions)",
                    javaClass -> javaClass.getPackageName().startsWith("com.thiru.wealthlens.shared.util")
                            && javaClass.getSimpleName().startsWith("T"));

    /** Duplicate simple names that predate W-045a. Owed: 03-WORK-ITEMS.md Appendix F. May only shrink. */
    private static final Set<String> DUPLICATE_NAMES_BEFORE_W045A = Set.of("DateRange", "ExcelParser");


    /**
     * CLAUDE.md "Engineering standards" §3: a generic suffix means the responsibility is not yet
     * understood. The classes listed predate the rule (D-045, W-045a); the list may only shrink.
     */
    @ArchTest
    static final ArchRule classes_have_no_generic_suffix =
            noClasses()
                    .that().areNotAnonymousClasses()
                    .and(DescribedPredicate.not(GRANDFATHERED_GENERIC_NAMES))
                    .and(DescribedPredicate.not(SHARED_T_PREFIXED_UTILITIES))
                    .should().haveSimpleNameEndingWith("Helper")
                    .orShould().haveSimpleNameEndingWith("Util")
                    .orShould().haveSimpleNameEndingWith("Utils")
                    .orShould().haveSimpleNameEndingWith("Manager")
                    .orShould().haveSimpleNameEndingWith("Impl")
                    .orShould().haveSimpleNameEndingWith("Data")
                    .orShould().haveSimpleNameEndingWith("Info")
                    .orShould().haveSimpleNameEndingWith("Processor")
                    .orShould().haveSimpleNameEndingWith("Common")
                    .orShould().haveSimpleNameEndingWith("Misc")
                    .because("CLAUDE.md §3: a class name states the domain concept, not a generic role");

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
                    .and().resideOutsideOfPackage("..taxengine.rules.money..")
                    .and().haveSimpleNameNotEndingWith("ChargeRounding")
                    .should().callMethod(java.math.BigDecimal.class, "setScale", int.class, java.math.RoundingMode.class)
                    .because("TL-8: paise rounding belongs to TMoney; a second copy is how two roundings diverge."
                            + " taxengine.rules.money is the second permitted home, not a third copy: TL-8 section 7 names"
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
     * Money is {@code Money}, and rates are {@code Rate}.
     *
     * <p>Expressed as a dependency rule rather than a field rule because {@code taxengine} has no
     * fields yet, and ArchUnit 1.3 fails a rule that checks nothing. It is also stronger: it catches
     * a {@code BigDecimal} parameter, return type or local, not only a field.
     */
    @ArchTest
    static final ArchRule tax_modules_use_money_not_bigdecimal =
            noClasses()
                    .that().resideInAPackage("..taxengine..")
                    .and().resideOutsideOfPackage("..taxengine.rules.money..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("java.math.BigDecimal")
                    .because("D-MONEY: money is Money, rates are Rate, and nothing else is either."
                            + " Money lives in taxengine.rules.money because a rule pack holds rupee figures --"
                            + " slab band bounds, the 80C ceiling, the standard deduction -- so the rules"
                            + " vocabulary needs it by its nature (B-001, W-013; package since D-048).");

    /**
     * The rules vocabulary never reaches into the engine that runs it.
     *
     * <p>Until decision D-048 {@code taxrules} was a module of its own and Spring Modulith enforced this
     * direction. Folding it into {@code taxengine} kept every other reason for the split and lost only
     * that check, so it is restated here: a cap shape or a pack type that imported a node, the pipeline
     * or a result would make the law depend on how it happens to be evaluated.
     */
    @ArchTest
    static final ArchRule rules_do_not_depend_on_the_runner =
            noClasses()
                    .that().resideInAPackage("..taxengine.rules..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..taxengine.input..", "..taxengine.pipeline..",
                            "..taxengine.trace..", "..taxengine.result..")
                    .because("D-048: the law is data plus the arithmetic of single rules; ordering, carrying"
                            + " values and tracing belong to the runner, which may read the rules and never"
                            + " the reverse.");

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

    /**
     * L5 guard 3 — {@code adjustPipeline} overrides are listed, and the list is reviewed.
     *
     * <p>{@code 17-regime-lifecycle.md} L5 makes tier-3 {@code RegimeBehaviour} the tier that can
     * rot. A regime that rewrites the assembled pipeline is the easiest way to make the computation
     * stop matching the rulebook, and it is invisible in review unless something points at it.
     * Overriding is allowed; doing so <b>invisibly</b> is not — so this lists every overrider and
     * fails until the list below is updated deliberately.
     *
     * <p>Written as a plain {@code @Test} rather than an {@code @ArchTest} on purpose. The expected
     * number of overriders is <b>zero</b>, and under ArchUnit with no {@code archunit.properties} a
     * rule matching nothing fails the build — so an {@code @ArchTest} here would break CI on day one
     * instead of guarding anything. {@code W-031b}'s erratum on L5 says exactly this. The shape is
     * the withdrawn-abstraction gate above.
     *
     * <p>L5's own recommendation is to ship with zero implementations beyond
     * {@code DataDrivenRegimeBehaviour}. An entry appearing here is the signal to ask whether the
     * {@code Condition} algebra is missing a predicate instead.
     */
    @Test
    void adjustPipeline_overrides_are_listed_and_reviewed() throws IOException {
        // Empty on purpose: no regime needs tier-3 behaviour today (L5's RECOMMENDATION).
        // Adding a name here is a deliberate act, which is the whole point of the guard.
        List<String> reviewed = List.of();

        String hook = "adjustPipeline";
        Path root = Path.of("src/main/java");
        try (Stream<Path> paths = Files.walk(root)) {
            List<String> overriders = paths
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    // The interface that declares the hook is not an override of it.
                    .filter(f -> !f.endsWith("RegimeBehaviour.java"))
                    .filter(f -> {
                        try {
                            return Files.readString(f).contains(hook);
                        } catch (IOException e) {
                            throw new IllegalStateException("could not read " + f, e);
                        }
                    })
                    .map(f -> f.getFileName().toString().replace(".java", ""))
                    .sorted()
                    .toList();

            if (!overriders.equals(reviewed)) {
                throw new AssertionError(
                        "RegimeBehaviour.adjustPipeline rewrites the assembled pipeline, so every"
                                + " override is a review trigger (17-regime-lifecycle.md L5, guard 3)."
                                + " Reviewed: " + reviewed + ", found: " + overriders
                                + ". If this override is intended, add it to the reviewed list in this"
                                + " test -- and first ask whether the Condition algebra is missing a"
                                + " predicate, which would serve every regime rather than one.");
            }
        }
    }


    /**
     * CLAUDE.md "Engineering standards" §3: one word per concept. Two top-level classes sharing a
     * simple name in different modules make every import a guess (D-045, W-045a).
     */
    @Test
    void simple_class_names_are_unique() {
        Map<String, List<String>> byName = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.thiru.wealthlens")
                .stream()
                .filter(javaClass -> javaClass.isTopLevelClass())
                .filter(javaClass -> !javaClass.getSimpleName().equals("package-info"))
                .collect(Collectors.groupingBy(
                        JavaClass::getSimpleName,
                        TreeMap::new,
                        Collectors.mapping(JavaClass::getName, Collectors.toList())));

        Map<String, List<String>> duplicates = new TreeMap<>();
        byName.forEach((name, classes) -> {
            if (classes.size() > 1 && !DUPLICATE_NAMES_BEFORE_W045A.contains(name)) {
                duplicates.put(name, classes);
            }
        });

        assertThat(duplicates)
                .as("top-level classes sharing a simple name — rename one to the concept it states")
                .isEmpty();
    }
}
