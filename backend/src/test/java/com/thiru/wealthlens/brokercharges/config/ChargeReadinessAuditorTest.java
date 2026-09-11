package com.thiru.wealthlens.brokercharges.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.testsupport.LogCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Says so at startup when the engine is switched on but has nothing to price with.
 *
 * <p>This is the one deployment mistake that is silent by construction. {@code POST /charges/seed}
 * is a deliberate act since ADR-27, so a fresh database holds no rate cards until somebody asks.
 * Deploy with {@code shadow-recording} or {@code authoritative} on before seeding and every trade
 * resolves {@code NO_SCHEDULE}: nothing errors, nothing fails, the charge is simply zero, and the
 * gap surfaces only in a report nobody is reading yet.
 *
 * <p>Modelled on {@code TransactionSafetyAuditor}, which exists for the same reason — a capability
 * that is off says nothing about itself unless something checks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChargeReadinessAuditorTest {

    @Mock
    private ChargeScheduleRepository chargeScheduleRepository;

    private ChargeReadinessAuditor auditor(boolean engineEnabled, boolean shadow, boolean authoritative) {
        return new ChargeReadinessAuditor(
                new ChargeEngineProperties(engineEnabled, shadow, authoritative), chargeScheduleRepository);
    }

    @Test
    void audit_whenTheEngineIsLiveButNoRateCardExists_warnsLoudly() {
        when(chargeScheduleRepository.count()).thenReturn(0L);

        try (LogCapture logs = LogCapture.on(ChargeReadinessAuditor.class)) {
            auditor(true, true, false).audit();
            assertThat(logs.warnings()).anyMatch(m -> m.contains("POST /charges/seed"));
        }
    }

    /** The dangerous combination: authoritative, with nothing to price against. */
    @Test
    void audit_whenAuthoritativeWithNoRateCards_saysWhatItWillCost() {
        when(chargeScheduleRepository.count()).thenReturn(0L);

        try (LogCapture logs = LogCapture.on(ChargeReadinessAuditor.class)) {
            auditor(true, false, true).audit();
            assertThat(logs.warnings()).anyMatch(m -> m.contains("NO_SCHEDULE"));
        }
    }

    @Test
    void audit_whenRateCardsArePresent_confirmsReadinessWithoutWarning() {
        when(chargeScheduleRepository.count()).thenReturn(11L);

        try (LogCapture logs = LogCapture.on(ChargeReadinessAuditor.class)) {
            auditor(true, true, true).audit();
            assertThat(logs.warnings()).isEmpty();
            assertThat(logs.messages()).anyMatch(m -> m.contains("11"));
        }
    }

    /**
     * Nothing records or prices, so an unseeded database is simply one nobody has seeded yet.
     * Warning here would train people to ignore the warning.
     */
    @Test
    void audit_whenBothPhaseFlagsAreOff_saysNothingAboutSeedData() {
        try (LogCapture logs = LogCapture.on(ChargeReadinessAuditor.class)) {
            auditor(true, false, false).audit();
            assertThat(logs.warnings()).isEmpty();
        }
    }

    @Test
    void audit_whenTheEngineIsDisabled_saysNothing() {
        try (LogCapture logs = LogCapture.on(ChargeReadinessAuditor.class)) {
            auditor(false, true, true).audit();
            assertThat(logs.warnings()).isEmpty();
        }
    }
}
