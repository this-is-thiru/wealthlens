package com.thiru.wealthlens.taxengine.rules.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.component.CashNature;
import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.ComponentKind;
import com.thiru.wealthlens.taxengine.rules.component.ComponentLifecycle;
import com.thiru.wealthlens.taxengine.rules.component.TaxTreatment;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.condition.IsResident;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.ConditionSet;
import com.thiru.wealthlens.taxengine.rules.pack.DisallowanceSet;
import com.thiru.wealthlens.taxengine.rules.pack.MidYearCombination;
import com.thiru.wealthlens.taxengine.rules.pack.RegimeApplicability;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.regime.BehaviourId;
import com.thiru.wealthlens.taxengine.rules.regime.BindingSpec;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.regime.ContinuationPolicy;
import com.thiru.wealthlens.taxengine.rules.regime.DeadlineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionKind;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionProtocol;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionVehicle;
import com.thiru.wealthlens.taxengine.rules.regime.InvalidationPolicy;
import com.thiru.wealthlens.taxengine.rules.regime.PipelineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeBehaviour;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeBehaviourRegistry;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeLifecycle;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.registry.DraftOrFinal;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationStatus;
import com.thiru.wealthlens.taxengine.rules.temporal.AmendmentRef;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One clean case and one violating case per implemented invariant.
 *
 * <p>Packs are built in code rather than loaded from files: an invariant is about what a pack
 * <em>means</em>, and a fixture file would test the parser as well.
 */
class PackValidatorTest {

    private static final TaxYearId TY = new TaxYearId(2026);
    private static final RuleId SLAB = new RuleId("RATE-SLAB-NEW-TAX-REGIME");
    private static final RuleId DISALLOW = new RuleId("REG-NEW-DISALLOW-001");
    private static final RuleId ELIG = new RuleId("REG-ELIG-001");
    private static final RuleId HRA = new RuleId("SAL-HRA-001");
    private static final RegimeCode DEFAULT_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final DisallowanceSetId DIS_SET = new DisallowanceSetId("DIS-S202-2A");
    private static final ConditionSetId COND_SET = new ConditionSetId("ELIG-S205-2");
    private static final ComponentCode EMPLOYEE_PF = new ComponentCode("EMPLOYEE_PF");

    private static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                "Income-tax Act 2025",
                DraftOrFinal.FINAL,
                LocalDate.of(2026, 9, 19),
                VerificationStatus.VERIFIED,
                null);
    }

    /** Every rule version must name the instrument that introduced it — invariant I15. */
    private static AmendmentRef introducedByFinanceAct() {
        return new AmendmentRef(
                "Finance Act, 2026 (4 of 2026), s.38",
                "CG-DL-E-31032026-271439",
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 1),
                false,
                URI.create("https://egazette.gov.in/WriteReadData/2026/271439.pdf"),
                VerificationStatus.VERIFIED);
    }

    private static RuleRow rule(RuleId id, CapShape cap) {
        return new RuleRow(
                id,
                TemporalAxis.TAX_YEAR,
                List.of(new RuleVersion(
                        Validity.Between.fromOnwards(LocalDate.of(2026, 4, 1)),
                        new RegimeApplicability.AllRegimes(),
                        cap,
                        List.of(),
                        Provision.notYetVerified("Income-tax Act, 1961"),
                        Provision.present("Income-tax Act, 2025", "s.202(1)"),
                        introducedByFinanceAct(),
                        provenance())),
                provenance());
    }

    private static RegimeSpec regime(
            RegimeCode code, boolean isDefault, Set<DisallowanceSetId> dis, Set<ConditionSetId> elig) {
        return new RegimeSpec(
                code,
                "Regime " + code,
                Provision.notYetVerified("Income-tax Act, 1961"),
                Provision.present("Income-tax Act, 2025", "s.202(1)"),
                Set.of(PersonType.INDIVIDUAL),
                SLAB,
                dis,
                elig,
                new ElectionProtocol(
                        isDefault ? ElectionKind.DEFAULT_NO_ACTION : ElectionKind.OPT_OUT,
                        isDefault ? ElectionVehicle.NONE : ElectionVehicle.IN_THE_RETURN,
                        Optional.empty(),
                        isDefault ? new DeadlineSpec.NoDeadline() : new DeadlineSpec.DueDateUnder("263(1)"),
                        BindingSpec.free(),
                        provenance()),
                new RegimeLifecycle(
                        DateRange.fromOnwards(LocalDate.of(2024, 4, 1)),
                        DateRange.fromOnwards(LocalDate.of(2024, 4, 1)),
                        new ContinuationPolicy.ContinueIndefinitely(),
                        new InvalidationPolicy.NoInvalidation(),
                        provenance()),
                isDefault,
                new PipelineSpec(List.of(new NodeId("tax.bybasis"))),
                Optional.empty(),
                provenance());
    }

    private static Map<RulePackLayer, RulePackRef> layers() {
        Map<RulePackLayer, RulePackRef> map = new EnumMap<>(RulePackLayer.class);
        for (RulePackLayer layer : RulePackLayer.values()) {
            String name = layer.name().toLowerCase(java.util.Locale.ROOT);
            map.put(layer, new RulePackRef(layer, "2026-27." + name + ".1", "sha256:" + "a".repeat(64)));
        }
        return map;
    }

    private static GoverningInstruments instruments() {
        Instrument act = Instrument.assented(
                "Income-tax Act, 2025",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-21082025-000000",
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                VerificationStatus.VERIFIED);
        Instrument rules = Instrument.notified(
                "Income-tax Rules, 2026",
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-20032026-271092",
                URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                VerificationStatus.VERIFIED);
        return new GoverningInstruments(act, act, rules);
    }

    /** A pack that validates: one default regime, every reference resolving. */
    private static PackBuilder validPack() {
        return new PackBuilder();
    }

    private static final class PackBuilder {
        Map<RuleId, RuleRow> rules = new LinkedHashMap<>();
        Map<ComponentCode, ComponentDefinition> components = new LinkedHashMap<>();
        Map<RegimeCode, RegimeSpec> regimes = new LinkedHashMap<>();
        Map<DisallowanceSetId, DisallowanceSet> disallowanceSets = new LinkedHashMap<>();
        Map<ConditionSetId, ConditionSet> conditionSets = new LinkedHashMap<>();

        PackBuilder() {
            rules.put(SLAB, rule(SLAB, new Fixed(Money.ZERO)));
            rules.put(DISALLOW, rule(DISALLOW, new Fixed(Money.ZERO)));
            rules.put(ELIG, rule(ELIG, new Fixed(Money.ZERO)));
            rules.put(HRA, rule(HRA, new Fixed(Money.ZERO)));
            disallowanceSets.put(
                    DIS_SET,
                    new DisallowanceSet(DIS_SET, "s.202(2)(a)", Set.of(HRA), DISALLOW, provenance()));
            conditionSets.put(
                    COND_SET,
                    new ConditionSet(
                            COND_SET, "s.205(2)", List.of(new IsResident()), ELIG, provenance()));
            regimes.put(DEFAULT_REGIME, regime(DEFAULT_REGIME, true, Set.of(DIS_SET), Set.of(COND_SET)));
        }

        RulePack build() {
            return new RulePack(
                    TY, layers(), instruments(), rules, components, regimes, disallowanceSets, conditionSets);
        }
    }

    @Nested
    @DisplayName("a pack whose references all resolve")
    class Clean {

        @Test
        void validates() {
            assertThatCode(() -> PackValidator.validate(validPack().build())).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("I2 — every rule reference resolves")
    class RuleReferences {

        @Test
        void aRegimesRateTableMustExist() {
            PackBuilder pack = validPack();
            pack.rules.remove(SLAB);

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("rate table names rule RATE-SLAB-NEW-TAX-REGIME");
        }

        @Test
        void aDisallowanceSetsRulesMustExist() {
            PackBuilder pack = validPack();
            pack.disallowanceSets.put(
                    DIS_SET,
                    new DisallowanceSet(
                            DIS_SET, "s.202(2)(a)", Set.of(new RuleId("SAL-LTA-001")), DISALLOW, provenance()));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("SAL-LTA-001");
        }

        @Test
        void aComponentsFeedsIntoMustExist() {
            PackBuilder pack = validPack();
            pack.components.put(
                    EMPLOYEE_PF,
                    new ComponentDefinition(
                            EMPLOYEE_PF,
                            "Employee provident fund",
                            HeadCode.SALARIES,
                            ComponentKind.EMPLOYEE_CONTRIBUTION,
                            CashNature.CASH_OUTFLOW,
                            TaxTreatment.DEDUCTIBLE,
                            Set.of(new RuleId("DED-80C-001")),
                            Set.of(),
                            false,
                            ComponentLifecycle.openEndedFrom(LocalDate.of(2026, 4, 1)),
                            provenance()));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("feedsInto names rule DED-80C-001");
        }

        @Test
        void anInterimRuleForAnUnnotifiedProvisionMustExist() {
            // PendingNotification names what applies until commencement; a dangling name means the
            // product cannot say what the taxpayer should do in the meantime
            PackBuilder pack = validPack();
            pack.rules.put(
                    new RuleId("SAL-DISPENSION-001"),
                    new RuleRow(
                            new RuleId("SAL-DISPENSION-001"),
                            TemporalAxis.TAX_YEAR,
                            List.of(new RuleVersion(
                                    new Validity.PendingNotification(
                                            new RuleId("SAL-DISPENSION-999"),
                                            "the pre-amendment exemption continues",
                                            provenance()),
                                    new RegimeApplicability.AllRegimes(),
                                    new Fixed(Money.ZERO),
                                    List.of(),
                                    Provision.notYetVerified("Income-tax Act, 1961"),
                                    Provision.notYetVerified("Income-tax Act, 2025"),
                                    introducedByFinanceAct(),
                                    provenance())),
                            provenance()));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("SAL-DISPENSION-999");
        }
    }

    @Nested
    @DisplayName("I5 — exactly one default regime")
    class DefaultRegime {

        @Test
        void twoDefaultsAreRejected() {
            PackBuilder pack = validPack();
            RegimeCode second = new RegimeCode("S202_ALSO_DEFAULT");
            pack.regimes.put(second, regime(second, true, Set.of(), Set.of()));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("2 default regimes")
                    .hasMessageContaining("pick one arbitrarily");
        }

        @Test
        void noDefaultIsRejected() {
            PackBuilder pack = validPack();
            pack.regimes.put(DEFAULT_REGIME, regime(DEFAULT_REGIME, false, Set.of(DIS_SET), Set.of(COND_SET)));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("no default regime");
        }
    }

    @Nested
    @DisplayName("I6 — regime set references resolve")
    class SetReferences {

        @Test
        void aMissingDisallowanceSetIsRejected() {
            PackBuilder pack = validPack();
            pack.disallowanceSets.clear();

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("silently allowed");
        }

        @Test
        void aMissingConditionSetIsRejected() {
            PackBuilder pack = validPack();
            pack.conditionSets.clear();

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("treated as eligible");
        }
    }

    @Nested
    @DisplayName("I7 — component references resolve")
    class ComponentReferences {

        @Test
        void anAggregatedComponentMustBeInTheRegistry() {
            PackBuilder pack = validPack();
            pack.rules.put(
                    new RuleId("DED-80C-001"),
                    rule(
                            new RuleId("DED-80C-001"),
                            new AggregateWithCeiling(
                                    List.of(EMPLOYEE_PF), Money.of("150000"), Map.of())));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("EMPLOYEE_PF")
                    .hasMessageContaining("missing from the total with no error");
        }
    }

    @Nested
    @DisplayName("reporting")
    class Reporting {

        @Test
        void everyViolationIsReportedTogether() {
            // Fixing one reference per startup is a bad afternoon
            PackBuilder pack = validPack();
            pack.rules.remove(SLAB);
            pack.disallowanceSets.clear();
            pack.conditionSets.clear();

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("unresolved reference(s)")
                    .hasMessageContaining("RATE-SLAB-NEW-TAX-REGIME")
                    .hasMessageContaining("DIS-S202-2A")
                    .hasMessageContaining("ELIG-S205-2");
        }
    }

    @Nested
    @DisplayName("I1 — enforced at construction, not here")
    class Provenances {

        @Test
        void anUnverifiedProvenanceWithoutWhatToCheckCannotBeBuilt() {
            // So a validator branch for I1 would be unreachable code
            assertThatThrownBy(() ->
                            new Provenance(
                                    URI.create("https://egazette.gov.in/x.pdf"),
                                    "somewhere",
                                    DraftOrFinal.FINAL,
                                    LocalDate.of(2026, 9, 19),
                                    VerificationStatus.NEEDS_VERIFICATION,
                                    "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void aVerifiedProvenanceNeedsNoWhatToCheck() {
            assertThat(provenance().status()).isEqualTo(VerificationStatus.VERIFIED);
        }
    }

    @Nested
    @DisplayName("I13 — the year is covered, with no gap and no overlap")
    class YearCoverage {

        private static RuleRow withVersions(RuleVersion... versions) {
            return new RuleRow(SLAB, TemporalAxis.TAX_YEAR, List.of(versions), provenance());
        }

        private static RuleVersion versionFrom(LocalDate from, LocalDate to) {
            return new RuleVersion(
                    new Validity.Between(from, Optional.ofNullable(to)),
                    new RegimeApplicability.AllRegimes(),
                    new Fixed(Money.ZERO),
                    List.of(),
                    Provision.notYetVerified("Income-tax Act, 1961"),
                    Provision.present("Income-tax Act, 2025", "s.202(1)"),
                    introducedByFinanceAct(),
                    provenance());
        }

        @Test
        void aGapInTheYearIsRejected() {
            // The law cannot say nothing for part of a year
            PackBuilder pack = validPack();
            pack.rules.put(SLAB, withVersions(versionFrom(LocalDate.of(2026, 9, 1), null)));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("2026-04-01")
                    .hasMessageContaining("the tax year has a gap");
        }

        @Test
        void twoVersionsInForceOnTheSameDayAreRejected() {
            // versionAt would silently take whichever was listed first
            PackBuilder pack = validPack();
            pack.rules.put(
                    SLAB,
                    new RuleRow(
                            SLAB,
                            TemporalAxis.TAX_YEAR,
                            List.of(
                                    versionFrom(LocalDate.of(2020, 4, 1), null),
                                    versionFrom(LocalDate.of(2021, 4, 1), null)),
                            MidYearCombination.LATEST_VERSION_GOVERNS,
                            null,
                            provenance()));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("versions in force on")
                    .hasMessageContaining("first listed");
        }

        @Test
        void aRuleWithNoVersionInTheYearIsRejected() {
            PackBuilder pack = validPack();
            pack.rules.put(
                    SLAB,
                    withVersions(versionFrom(LocalDate.of(2030, 4, 1), null)));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("cannot say nothing for a year");
        }

        @Test
        void anAcquisitionDateRuleIsNotRequiredToCoverTheYear() {
            // A rule keyed on when an asset was bought legitimately has nothing in force this year
            PackBuilder pack = validPack();
            pack.rules.put(
                    new RuleId("CG-INDEX-001"),
                    new RuleRow(
                            new RuleId("CG-INDEX-001"),
                            TemporalAxis.ACQUISITION_DATE,
                            List.of(versionFrom(LocalDate.of(2001, 4, 1), LocalDate.of(2017, 3, 31))),
                            provenance()));

            assertThatCode(() -> PackValidator.validate(pack.build())).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("I14 — a mid-year change must say how its versions combine")
    class MidYearChange {

        private static RuleRow changingMidYear(MidYearCombination combination, RuleId combinationRule) {
            return new RuleRow(
                    SLAB,
                    TemporalAxis.TAX_YEAR,
                    List.of(
                            new RuleVersion(
                                    new Validity.Between(
                                            LocalDate.of(2026, 4, 1), Optional.of(LocalDate.of(2026, 8, 31))),
                                    new RegimeApplicability.AllRegimes(),
                                    new Fixed(Money.ZERO),
                                    List.of(),
                                    Provision.notYetVerified("Income-tax Act, 1961"),
                                    Provision.present("Income-tax Act, 2025", "s.202(1)"),
                                    introducedByFinanceAct(),
                                    provenance()),
                            new RuleVersion(
                                    Validity.Between.fromOnwards(LocalDate.of(2026, 9, 1)),
                                    new RegimeApplicability.AllRegimes(),
                                    new Fixed(Money.of("1")),
                                    List.of(),
                                    Provision.notYetVerified("Income-tax Act, 1961"),
                                    Provision.present("Income-tax Act, 2025", "s.202(1)"),
                                    introducedByFinanceAct(),
                                    provenance())),
                    combination,
                    combinationRule,
                    provenance());
        }

        @Test
        void undeterminedIsRejectedWhenTheRuleActuallyChanges() {
            // Guessing produces a plausible number, only in the year the rule moved, that no
            // golden test written before the change would catch
            PackBuilder pack = validPack();
            pack.rules.put(SLAB, changingMidYear(MidYearCombination.UNDETERMINED, null));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("changes inside 2026-27")
                    .hasMessageContaining("rather than let the engine guess");
        }

        @Test
        void aDeclaredCombinationIsAccepted() {
            PackBuilder pack = validPack();
            pack.rules.put(SLAB, changingMidYear(MidYearCombination.PRORATE_PER_SEGMENT, null));

            assertThatCode(() -> PackValidator.validate(pack.build())).doesNotThrowAnyException();
        }

        @Test
        void undeterminedIsFineWhenTheRuleDoesNotChange() {
            // The honest default costs nothing until a change is actually authored
            assertThatCode(() -> PackValidator.validate(validPack().build())).doesNotThrowAnyException();
        }

        @Test
        void statutoryCombinationMustNameAnExistingRule() {
            PackBuilder pack = validPack();
            pack.rules.put(
                    SLAB, changingMidYear(MidYearCombination.STATUTORY, new RuleId("SAL-NOWHERE-001")));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("SAL-NOWHERE-001");
        }

        @Test
        void namingACombinationRuleWithoutStatutoryIsRejectedAtConstruction() {
            assertThatThrownBy(() ->
                            changingMidYear(MidYearCombination.PRORATE_PER_SEGMENT, new RuleId("SAL-HRA-001")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("would never consult it");
        }
    }

    @Nested
    @DisplayName("I15 — every version names the instrument that introduced it")
    class AmendmentHistory {

        @Test
        void aVersionWithNoAmendmentIsRejected() {
            // The ordered version list IS the amendment history
            PackBuilder pack = validPack();
            pack.rules.put(
                    SLAB,
                    new RuleRow(
                            SLAB,
                            TemporalAxis.TAX_YEAR,
                            List.of(new RuleVersion(
                                    Validity.Between.fromOnwards(LocalDate.of(2026, 4, 1)),
                                    new RegimeApplicability.AllRegimes(),
                                    new Fixed(Money.ZERO),
                                    List.of(),
                                    Provision.notYetVerified("Income-tax Act, 1961"),
                                    Provision.present("Income-tax Act, 2025", "s.202(1)"),
                                    null,
                                    provenance())),
                            provenance()));

            assertThatThrownBy(() -> PackValidator.validate(pack.build()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("names no AmendmentRef")
                    .hasMessageContaining("not where it came from");
        }
    }

    @Nested
    @DisplayName("I9 — behaviours resolve, in both directions")
    class Behaviours {

        static final BehaviourId NOVEL = new BehaviourId("S999_NOVEL_ELECTION");

        record SyntheticBehaviour(BehaviourId id) implements RegimeBehaviour {}

        private static RegimeSpec regimeNaming(BehaviourId behaviour) {
            RegimeSpec base = regime(DEFAULT_REGIME, true, Set.of(DIS_SET), Set.of(COND_SET));
            return new RegimeSpec(
                    base.code(), base.displayName(), base.provision1961(), base.provision2025(),
                    base.appliesTo(), base.rateTable(), base.disallowances(), base.eligibility(),
                    base.election(), base.lifecycle(), base.isDefault(), base.pipeline(),
                    Optional.ofNullable(behaviour), base.provenance());
        }

        @Test
        void aPackNamingAnUnimplementedBehaviourIsRefused() {
            // Its special handling would silently not run -- a plausible wrong answer for everyone
            // in that regime, in the one place the design allows arbitrary code
            PackBuilder pack = validPack();
            pack.regimes.put(DEFAULT_REGIME, regimeNaming(NOVEL));

            assertThatThrownBy(() ->
                            PackValidator.validate(pack.build(), RegimeBehaviourRegistry.empty()))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("S999_NOVEL_ELECTION")
                    .hasMessageContaining("would silently not run");
        }

        @Test
        void aRegisteredBehaviourNoPackReferencesIsRefused() {
            // D-ONEIMPL reachability, extended to behaviours. Unlike I3 this IS checkable at load:
            // the registry is in hand, and a behaviour is referenced by a regime directly.
            assertThatThrownBy(() ->
                            PackValidator.validate(
                                    validPack().build(),
                                    RegimeBehaviourRegistry.of(List.of(new SyntheticBehaviour(NOVEL)))))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("dead code that looks alive");
        }

        @Test
        void aMatchedPairValidates() {
            PackBuilder pack = validPack();
            pack.regimes.put(DEFAULT_REGIME, regimeNaming(NOVEL));

            assertThatCode(() ->
                            PackValidator.validate(
                                    pack.build(),
                                    RegimeBehaviourRegistry.of(List.of(new SyntheticBehaviour(NOVEL)))))
                    .doesNotThrowAnyException();
        }

        @Test
        void theShippingStateIsNeitherSide() {
            // No pack names one, nothing is registered: both checks pass vacuously, which is
            // exactly what L5 recommends shipping
            assertThatCode(() ->
                            PackValidator.validate(validPack().build(), RegimeBehaviourRegistry.empty()))
                    .doesNotThrowAnyException();
        }
    }
}
