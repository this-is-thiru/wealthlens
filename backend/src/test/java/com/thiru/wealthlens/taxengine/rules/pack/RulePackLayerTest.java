package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.LegalStatus;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.AmendmentRef;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The four layers, and the one property that justifies having four.
 *
 * <p>G-11: a computation caches on {@code law} and {@code exec} only, so editorial work on the claim
 * catalogue costs nothing. If that stops holding, the layering is pure overhead.
 */
class RulePackLayerTest {

    private static final String SHA_A = "sha256:" + "a".repeat(64);
    private static final String SHA_B = "sha256:" + "b".repeat(64);
    private static final RuleId SLAB = new RuleId("RATE-SLAB-NEW-TAX-REGIME");

    private static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                "Income-tax Act 2025, s.202(1) Table",
                LocalDate.of(2026, 9, 19),
                VerificationLevel.SOURCE_CHECKED,
                null);
    }

    private static Instrument act() {
        return Instrument.assented(
                "Income-tax Act, 2025",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-21082025-000000",
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                VerificationLevel.SOURCE_CHECKED);
    }

    private static GoverningInstruments instruments() {
        return new GoverningInstruments(
                act(),
                Instrument.assented(
                        "Finance Act, 2026",
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 1),
                        "CG-DL-E-31032026-271439",
                        URI.create("https://egazette.gov.in/WriteReadData/2026/271439.pdf"),
                        VerificationLevel.SOURCE_CHECKED),
                Instrument.notified(
                        "Income-tax Rules, 2026",
                        LocalDate.of(2026, 4, 1),
                        "CG-DL-E-20032026-271092",
                        URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                        VerificationLevel.SOURCE_CHECKED));
    }

    private static Map<RulePackLayer, RulePackRef> layers(
            String law, String exec, String forms, String content) {
        Map<RulePackLayer, RulePackRef> map = new EnumMap<>(RulePackLayer.class);
        map.put(RulePackLayer.LAW, new RulePackRef(RulePackLayer.LAW, "2026-27.law." + law, SHA_A));
        map.put(RulePackLayer.EXEC, new RulePackRef(RulePackLayer.EXEC, "2026-27.exec." + exec, SHA_A));
        map.put(RulePackLayer.FORMS, new RulePackRef(RulePackLayer.FORMS, "2026-27.forms." + forms, SHA_A));
        map.put(
                RulePackLayer.CONTENT,
                new RulePackRef(RulePackLayer.CONTENT, "2026-27.content." + content, SHA_A));
        return map;
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
                VerificationLevel.SOURCE_CHECKED);
    }

    private static RuleRow slabRule() {
        return new RuleRow(
                SLAB,
                TemporalAxis.TAX_YEAR,
                List.of(new RuleVersion(
                        Validity.Between.fromOnwards(LocalDate.of(2026, 4, 1)),
                        new RegimeApplicability.AllRegimes(),
                        new Fixed(Money.ZERO),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        Provision.notYetVerified("Income-tax Act, 1961"),
                        Provision.present("Income-tax Act, 2025", "s.202(1)"),
                        introducedByFinanceAct(),
                        LegalStatus.IN_FORCE,
                        PublicationStatus.PUBLISHED,
                        provenance())),
                provenance());
    }

    private static RulePack pack(Map<RulePackLayer, RulePackRef> layers) {
        return new RulePack(
                new TaxYearId(2026),
                layers,
                instruments(),
                Map.of(SLAB, slabRule()), Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
    }

    @Nested
    @DisplayName("the compute cache keys on law and exec only — G-11")
    class CacheKey {

        @Test
        void sevenEditorialRevisionsInvalidateNothing() {
            // A2's own example: content is revised seven times
            String before = pack(layers("3", "1", "2", "1")).computationCacheKey();
            String after = pack(layers("3", "1", "2", "7")).computationCacheKey();

            assertThat(after).isEqualTo(before);
        }

        @Test
        void aFormNotificationInvalidatesNothing() {
            // CBDT notifies ITR forms on a different date from the Finance Act, and revised them
            // again mid-year — G.S.R. 822(E) substituted Forms 169 and 171 in September
            String before = pack(layers("3", "1", "2", "1")).computationCacheKey();
            String after = pack(layers("3", "1", "9", "1")).computationCacheKey();

            assertThat(after).isEqualTo(before);
        }

        @Test
        void aLawChangeInvalidatesEverything() {
            String before = pack(layers("3", "1", "2", "1")).computationCacheKey();
            String after = pack(layers("4", "1", "2", "1")).computationCacheKey();

            assertThat(after).isNotEqualTo(before);
        }

        @Test
        void anExecChangeInvalidatesEverything() {
            // exec changes no number directly, but it selects which law applies
            String before = pack(layers("3", "1", "2", "1")).computationCacheKey();
            String after = pack(layers("3", "2", "2", "1")).computationCacheKey();

            assertThat(after).isNotEqualTo(before);
        }

        @Test
        void aSameVersionedLayerWithDifferentBytesInvalidates() {
            // A layer edited without a version bump must not be served as the version it claims
            Map<RulePackLayer, RulePackRef> tampered = layers("3", "1", "2", "1");
            tampered.put(RulePackLayer.LAW, new RulePackRef(RulePackLayer.LAW, "2026-27.law.3", SHA_B));

            assertThat(pack(tampered).computationCacheKey())
                    .isNotEqualTo(pack(layers("3", "1", "2", "1")).computationCacheKey());
        }

        @Test
        void theKeyIsReadableAndNamesTheYear() {
            // A cache miss in a log should be diagnosable; there is nothing secret to compress away
            String key = pack(layers("3", "1", "2", "1")).computationCacheKey();

            assertThat(key).startsWith("2026-27|").contains("law=2026-27.law.3").contains("exec=");
            assertThat(key).doesNotContain("forms=").doesNotContain("content=");
        }
    }

    @Nested
    @DisplayName("which layers may change a number")
    class Authority {

        @Test
        void onlyLawAndExecAffectComputation() {
            assertThat(RulePackLayer.LAW.affectsComputation()).isTrue();
            assertThat(RulePackLayer.EXEC.affectsComputation()).isTrue();
            assertThat(RulePackLayer.FORMS.affectsComputation()).isFalse();
            assertThat(RulePackLayer.CONTENT.affectsComputation()).isFalse();
        }

        @Test
        void thereAreExactlyFourLayers() {
            assertThat(RulePackLayer.values()).hasSize(4);
        }
    }

    @Nested
    @DisplayName("manifest integrity")
    class Manifest {

        @Test
        void everyLayerMustBeVersioned() {
            Map<RulePackLayer, RulePackRef> incomplete = layers("3", "1", "2", "1");
            incomplete.remove(RulePackLayer.CONTENT);

            assertThatThrownBy(() -> pack(incomplete))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CONTENT");
        }

        @Test
        void aVersionMustNameItsOwnLayer() {
            // So a version quoted in a support ticket says which layer it belongs to
            assertThatThrownBy(() ->
                            new RulePackRef(RulePackLayer.LAW, "2026-27.content.7", SHA_A))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not name layer LAW");
        }

        @Test
        void aMalformedChecksumIsRejected() {
            assertThatThrownBy(() -> new RulePackRef(RulePackLayer.LAW, "2026-27.law.3", "deadbeef"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("64 lower-case hex digits");
        }

        @Test
        void aRuleFiledUnderTheWrongIdIsRejected() {
            assertThatThrownBy(() ->
                            new RulePack(
                                    new TaxYearId(2026),
                                    layers("3", "1", "2", "1"),
                                    instruments(),
                                    Map.of(new RuleId("SAL-HRA-001"), slabRule()), Map.of(),
                                    Map.of(),
                                    Map.of(),
                                    Map.of(),
                                    Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("calls itself");
        }

        @Test
        void anEmptyPackIsRejected() {
            assertThatThrownBy(() ->
                            new RulePack(
                                    new TaxYearId(2026),
                                    layers("3", "1", "2", "1"),
                                    instruments(),
                                    Map.of(), Map.of(),
                                    Map.of(),
                                    Map.of(),
                                    Map.of(),
                                    Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("would compute nothing");
        }

        @Test
        void anUnknownRuleFailsLoudly() {
            assertThatThrownBy(() -> pack(layers("3", "1", "2", "1")).rule(new RuleId("SAL-HRA-001")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has no rule");
        }
    }
}
