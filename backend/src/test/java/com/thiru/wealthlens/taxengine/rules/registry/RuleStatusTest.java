package com.thiru.wealthlens.taxengine.rules.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RegimeApplicability;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.AmendmentRef;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The three status axes of {@code 00-BRIEF.md} §5.3 (D-036, D-052): legal status and publication on
 * the version, verification on the source it was read from — and the three rules that tie them to
 * the pack.
 */
class RuleStatusTest {

    private static final TaxYearId TY = new TaxYearId(2026);
    private static final RuleId SLAB = new RuleId("RATE-SLAB-NEW-TAX-REGIME");
    private static final RuleId INTERIM = new RuleId("RATE-SLAB-FIRST-SCHEDULE");

    private static Provenance provenance(VerificationLevel level, String whatToCheck) {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                "Gazette CG-DL-E-22082025-265620, s.202(1) Table",
                LocalDate.of(2026, 9, 24),
                level,
                whatToCheck);
    }

    private static Provenance checked() {
        return provenance(VerificationLevel.SOURCE_CHECKED, null);
    }

    private static AmendmentRef amendment() {
        return new AmendmentRef(
                "Income-tax Act, 2025 (30 of 2025), s.202(1)",
                "CG-DL-E-22082025-265620",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                false,
                URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                VerificationLevel.SOURCE_CHECKED);
    }

    private static RuleVersion version(Validity validity, LegalStatus legal, PublicationStatus publication) {
        return new RuleVersion(
                validity,
                new RegimeApplicability.AllRegimes(),
                Optional.of(new Fixed(Money.of("60000"))),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Provision.present("Income-tax Act, 1961", "s.115BAC(1A)(iii)"),
                Provision.present("Income-tax Act, 2025", "s.202(1) Table"),
                amendment(),
                legal,
                publication,
                checked());
    }

    private static Validity inForce() {
        return Validity.Between.fromOnwards(LocalDate.of(2026, 4, 1));
    }

    private static Validity pending() {
        return new Validity.PendingNotification(INTERIM, "the first-schedule rates apply meanwhile", checked());
    }

    private static RulePack packWith(RuleVersion version) {
        Map<RulePackLayer, RulePackRef> layers = new EnumMap<>(RulePackLayer.class);
        for (RulePackLayer layer : RulePackLayer.values()) {
            String name = layer.name().toLowerCase(Locale.ROOT);
            layers.put(layer, new RulePackRef(layer, "2026-27." + name + ".1", "sha256:" + "a".repeat(64)));
        }
        Instrument act = Instrument.assented(
                "Income-tax Act, 2025 (30 of 2025)",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-22082025-265620",
                URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"),
                VerificationLevel.SOURCE_CHECKED);
        RuleRow row = new RuleRow(SLAB, TemporalAxis.TAX_YEAR, List.of(version), checked());
        return new RulePack(
                TY, layers, new GoverningInstruments(act, act, act), Map.of(SLAB, row), null, null, null, null, null);
    }

    @ParameterizedTest
    @EnumSource(value = PublicationStatus.class, names = "PUBLISHED", mode = EnumSource.Mode.EXCLUDE)
    void rulePack_whenAVersionIsNotPublished_isRefusedNamingTheRule(PublicationStatus status) {
        // Given
        RuleVersion unreleased = version(inForce(), LegalStatus.IN_FORCE, status);

        // When / Then
        assertThatThrownBy(() -> packWith(unreleased))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SLAB.value())
                .hasMessageContaining(status.name())
                .hasMessageContaining("PUBLISHED");
    }

    @Test
    void rulePack_whenEveryVersionIsPublished_isAccepted() {
        // Given
        RuleVersion released = version(inForce(), LegalStatus.IN_FORCE, PublicationStatus.PUBLISHED);

        // When / Then
        assertThatCode(() -> packWith(released)).doesNotThrowAnyException();
    }

    @Test
    void provenance_whenUnverifiedWithoutWhatToCheck_isRefused() {
        // When / Then — invariant I1
        assertThatThrownBy(() -> provenance(VerificationLevel.UNVERIFIED, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whatToCheck");
    }

    @ParameterizedTest
    @EnumSource(value = VerificationLevel.class, names = "UNVERIFIED", mode = EnumSource.Mode.EXCLUDE)
    void provenance_whenCheckedWithoutWhatToCheck_isAcceptedAndNotFlagged(VerificationLevel level) {
        // When
        Provenance source = provenance(level, null);

        // Then
        assertThat(source.requiresUserVisibleFlag()).isFalse();
    }

    @Test
    void provenance_whenUnverified_isFlaggedToTheUser() {
        // When
        Provenance source = provenance(VerificationLevel.UNVERIFIED, "open the 1961 Act s.87A base text");

        // Then
        assertThat(source.requiresUserVisibleFlag()).isTrue();
    }

    @Test
    void ruleVersion_whenPendingNotificationStatusHasAnOrdinaryValidity_isRefused() {
        // When / Then
        assertThatThrownBy(() -> version(inForce(), LegalStatus.PENDING_NOTIFICATION, PublicationStatus.PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PENDING_NOTIFICATION")
                .hasMessageContaining("interim rule");
    }

    @ParameterizedTest
    @EnumSource(value = LegalStatus.class, names = "PENDING_NOTIFICATION", mode = EnumSource.Mode.EXCLUDE)
    void ruleVersion_whenPendingNotificationValidityHasAnotherLegalStatus_isRefused(LegalStatus status) {
        // When / Then
        assertThatThrownBy(() -> version(pending(), status, PublicationStatus.PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(status.name())
                .hasMessageContaining("PENDING_NOTIFICATION");
    }

    @Test
    void ruleVersion_whenBothSayPendingNotification_isAccepted() {
        // When / Then
        assertThatCode(() -> version(pending(), LegalStatus.PENDING_NOTIFICATION, PublicationStatus.PUBLISHED))
                .doesNotThrowAnyException();
    }
}
