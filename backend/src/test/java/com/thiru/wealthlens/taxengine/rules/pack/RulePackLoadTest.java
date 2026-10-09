package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.TaxYearRegistry;
import com.thiru.wealthlens.taxengine.rules.registry.TaxYearRegistryEntry;
import com.thiru.wealthlens.taxengine.rules.registry.UnsupportedTaxYearException;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidator;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The whole chain: classpath → lock → timeline → compiler → lock check → assembler → validator.
 *
 * <p>{@code 08-golden-tests.md} T9-1 … T9-7. Six of the seven are here; **T9-6** (a rule no pipeline
 * reaches, invariant I3) is deferred to {@code W-039} by decision D-016, because reachability means
 * "evaluated by some node" and that mapping lives in the engine.
 *
 * <p>Each root under {@code rulepacks-broken/} is the real timeline and lock differing in exactly one
 * way, so the defect <em>is</em> the diff. The years are synthetic — 2090-91 onward — so they can
 * never collide with a pack anyone means to ship.
 */
class RulePackLoadTest {

    private static final TaxYearId TY_2026 = new TaxYearId(2026);

    private static Instrument act() {
        return Instrument.assented(
                "Income-tax Act, 2025 (30 of 2025)",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-22082025-265620",
                URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                VerificationLevel.SOURCE_CHECKED);
    }

    private static GoverningInstruments instruments() {
        return new GoverningInstruments(
                act(),
                act(),
                Instrument.notified(
                        "Income-tax Rules, 2026",
                        LocalDate.of(2026, 4, 1),
                        "CG-DL-E-20032026-271092",
                        URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                        VerificationLevel.SOURCE_CHECKED));
    }

    private static RulePack load(String root, TaxYearId year) {
        return new ClasspathRulePackProvider(root, Set.of(year), new JsonPackAssembler(instruments()))
                .load(year);
    }

    @Nested
    @DisplayName("the shipped 2026-27 pack")
    class TheRealPack {

        @Test
        void loadsAndValidates() {
            RulePack pack = load("rulepacks", TY_2026);

            assertThatCode(() -> PackValidator.validate(pack)).doesNotThrowAnyException();
            assertThat(pack.rules()).hasSize(134);
            assertThat(pack.regimes()).hasSize(2);
        }

        @Test
        void computesTheRulebookFigureThroughTheWholeChain() {
            // Classpath bytes to a tax figure, with nothing stubbed. T2-1: ₹50,00,000 → ₹10,80,000
            RulePack pack = load("rulepacks", TY_2026);

            var slab = pack.rule(new RuleId("RATE-SLAB-NEW-TAX-REGIME")).versions().getFirst().cap();
            var ctx = CapContext.of(new BaseId("ROUNDED_TOTAL_INCOME"), Money.of("5000000"));

            assertThat(slab.apply(ctx).value()).isEqualTo(Money.of("1080000"));
        }

        @Test
        void carriesTheLayerVersionsAComputationReceiptNeeds() {
            RulePack pack = load("rulepacks", TY_2026);

            assertThat(pack.computationCacheKey())
                    .startsWith("2026-27|")
                    .contains("law=2026-27.law.58")
                    .doesNotContain("content=");
        }

        @Test
        void bothRegimesResolveTheirRateTables() {
            // Invariant I2 in the shipped data, reached through the loader rather than by reading
            RulePack pack = load("rulepacks", TY_2026);

            for (RegimeCode code : pack.regimes().keySet()) {
                assertThatCode(() -> pack.rule(pack.regimes().get(code).rateTable()))
                        .doesNotThrowAnyException();
            }
        }

        @Test
        void itHoldsTheShippedComponentsAndThisTestSaysWhich() {
            // A component arrives with the first rule that reads it (D-089): professional tax came with
            // SAL-TAX-ON-EMPLOYMENT (D-093), the pay lines with SAL-SALARY-INCLUDES (D-094).
            assertThat(load("rulepacks", TY_2026).components()).containsOnlyKeys(new ComponentCode("BASIC"), new ComponentCode("BONUS"), new ComponentCode("CHILDREN_EDUCATION_ALLOWANCE"), new ComponentCode("COMMISSION"), new ComponentCode("CONVEYANCE_ALLOWANCE"), new ComponentCode("DEARNESS_ALLOWANCE"), new ComponentCode("ENTERTAINMENT_ALLOWANCE"), new ComponentCode("HOSTEL_ALLOWANCE"), new ComponentCode("HRA"), new ComponentCode("LTA"), new ComponentCode("PROFESSIONAL_TAX"), new ComponentCode("SPECIAL_ALLOWANCE"), new ComponentCode("TRANSPORT_ALLOWANCE_DISABLED"), new ComponentCode("UNIFORM_ALLOWANCE"), new ComponentCode("GRATUITY"), new ComponentCode("LEAVE_ENCASHMENT"), new ComponentCode("COMMUTED_PENSION"), new ComponentCode("VRS_COMPENSATION"), new ComponentCode("RETRENCHMENT_COMPENSATION"), new ComponentCode("NOTICE_PAY"), new ComponentCode("DEFERRED_ANNUITY"), new ComponentCode("PROVIDENT_FUNDS_ACT_FUND"), new ComponentCode("NOTIFIED_PROVIDENT_FUND"), new ComponentCode("EMPLOYEE_PF"), new ComponentCode("EMPLOYEE_SUPERANNUATION"), new ComponentCode("NOTIFIED_DEPOSIT_SCHEME"), new ComponentCode("SAVINGS_CERTIFICATE"), new ComponentCode("MUTUAL_FUND_UNITS"), new ComponentCode("MUTUAL_FUND_PENSION_FUND"), new ComponentCode("NATIONAL_HOUSING_BANK_SCHEME"), new ComponentCode("HOUSING_FINANCE_DEPOSIT"), new ComponentCode("TUITION_FEES"), new ComponentCode("HOUSE_PURCHASE_PAYMENT"), new ComponentCode("BANK_TERM_DEPOSIT"), new ComponentCode("NABARD_BONDS"), new ComponentCode("SENIOR_CITIZEN_SAVINGS"), new ComponentCode("POST_OFFICE_TIME_DEPOSIT"), new ComponentCode("GOVERNMENT_PENSION_ADDITIONAL"), new ComponentCode("PENSION_FUND_ANNUITY"), new ComponentCode("EMPLOYEE_NPS"), new ComponentCode("ELIGIBLE_ISSUE_SUBSCRIPTION"), new ComponentCode("NPS_MINOR_DEPOSIT"), new ComponentCode("LIFE_INSURANCE_PREMIUM"), new ComponentCode("HEALTH_INSURANCE_FAMILY"), new ComponentCode("HEALTH_INSURANCE_PARENTS"), new ComponentCode("MEDICAL_EXPENDITURE_FAMILY"), new ComponentCode("MEDICAL_EXPENDITURE_PARENTS"), new ComponentCode("GOVERNMENT_DEFERRED_ANNUITY"), new ComponentCode("UNIT_LINKED_INSURANCE_PLAN"), new ComponentCode("MUTUAL_FUND_UNIT_LINKED_PLAN"), new ComponentCode("ANNUITY_PLAN"));
        }
    }

    @Nested
    @DisplayName("T9-1 — the pack for the requested year is missing")
    class MissingPack {

        @Test
        void loadFailsNamingTheAbsentFile() {
            assertThatThrownBy(() -> load("rulepacks", new TaxYearId(2035)))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("2035-36");
        }

        @Test
        void aDeclaredYearWithNoLockFailsNamingTheLock() {
            assertThatThrownBy(() ->
                            new ClasspathRulePackProvider(
                                            "rulepacks",
                                            Set.of(new TaxYearId(2035)),
                                            new JsonPackAssembler(instruments()))
                                    .load(new TaxYearId(2035)))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("rulepacks/2035-36.lock.json")
                    .hasMessageContaining("not on the classpath");
        }
    }

    @Nested
    @DisplayName("T9-2 — the year has no registry entry")
    class NoRegistryEntry {

        @Test
        void resolvingFailsNamingTheSupportedYears() {
            TaxYearRegistry registry = new TaxYearRegistry(Map.of(
                    TY_2026,
                    new TaxYearRegistryEntry(
                            TY_2026, instruments(), load("rulepacks", TY_2026).layers())));

            assertThatThrownBy(() -> registry.entryFor(new TaxYearId(2031)))
                    .isInstanceOf(UnsupportedTaxYearException.class)
                    .hasMessageContaining("2031-32 is not supported")
                    .hasMessageContaining("2026-27");
        }
    }

    @Nested
    @DisplayName("T9-3 — a layer's checksum does not match what the timeline compiles to")
    class ChecksumMismatch {

        @Test
        void loadFailsNamingThePackAndBothChecksums() {
            assertThatThrownBy(() -> load("rulepacks-broken/checksum-mismatch", new TaxYearId(2090)))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("LAW layer")
                    .hasMessageContaining("lock says sha256:" + "0".repeat(64))
                    .hasMessageContaining("timeline compiles to sha256:")
                    .hasMessageContaining("with a version bump");
        }

        @Test
        void aTimelineEditWithoutALockUpdateFailsTheLoadNamingTheRule() {
            assertThatThrownBy(() -> load("rulepacks-broken/rule-edited-without-lock", new TaxYearId(2094)))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("RATE-SLAB-NEW-TAX-REGIME changed")
                    .hasMessageNotContaining("RATE-SLAB-FIRST-SCHEDULE changed");
        }
    }

    @Nested
    @DisplayName("T9-4 — invariant I1, an unverified row with no whatToCheck")
    class BlankWhatToCheck {

        @Test
        void loadFailsAsAPackErrorNamingTheRow() {
            // Enforced by Provenance's own constructor, but surfaced as a pack error carrying the
            // path: E4 requires the loader to throw nothing else, and a pack author needs the row.
            // Refused when the timeline is read, so the error names the timeline file
            assertThatThrownBy(() -> load("rulepacks-broken/unverified-without-what-to-check", new TaxYearId(2091)))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("law/rules/RATE-SLAB-NEW-TAX-REGIME.json")
                    .hasMessageContaining("RATE-SLAB-NEW-TAX-REGIME")
                    .hasMessageContaining("whatToCheck");
        }
    }

    @Nested
    @DisplayName("T9-5 — invariant I2, a reference to a rule the pack does not define")
    class DanglingRuleReference {

        @Test
        void loadSucceedsButValidationFails() {
            // The distinction matters: parsing cannot see across files, so a dangling reference is
            // structurally fine and semantically wrong. The validator is what catches it.
            RulePack pack = load("rulepacks-broken/dangling-rate-table", new TaxYearId(2092));

            assertThatThrownBy(() -> PackValidator.validate(pack))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("RATE-SLAB-NEW-TAX-REGIME")
                    .hasMessageContaining("which the pack does not define");
        }
    }

    @Nested
    @DisplayName("T9-7 — the 400001 trap")
    class BandTrap {

        @Test
        void loadFailsAtParseTimeNamingTheRule() {
            assertThatThrownBy(() -> load("rulepacks-broken/lower-bound-400001", new TaxYearId(2093)))
                    .isInstanceOf(PackValidationException.class)
                    .hasMessageContaining("RATE-SLAB-BAND-002")
                    .hasMessageContaining("400001");
        }
    }
}
