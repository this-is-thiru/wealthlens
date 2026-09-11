package com.thiru.wealthlens.brokercharges.config;

import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Reports at startup whether the charges engine can actually price anything.
 *
 * <h2>Why this exists</h2>
 *
 * <p>It guards the one deployment mistake in this module that is silent by construction. Seeding is
 * a deliberate act since ADR-27, so a fresh database holds no rate cards until somebody calls
 * {@code POST /charges/seed}. Deploy with {@code shadow-recording} or {@code authoritative} switched
 * on before that has happened and every trade resolves {@code NO_SCHEDULE}: nothing throws, nothing
 * fails a health check, the charge is simply zero. Under {@code authoritative} that zero becomes the
 * cost basis, and the only trace is a gaps report nobody is reading on day one.
 *
 * <p>So the condition is announced where somebody will see it — the same reasoning as
 * {@code TransactionSafetyAuditor}, which exists because a disabled transaction manager is equally
 * quiet about itself.
 *
 * <p>It stays quiet when both phase flags are off: nothing is recording or pricing, so an unseeded
 * database is just one nobody has seeded yet, and warning about it would train people to ignore the
 * warning that matters.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ChargeReadinessAuditor {

    private final ChargeEngineProperties chargeEngineProperties;
    private final ChargeScheduleRepository chargeScheduleRepository;

    @PostConstruct
    public void audit() {
        if (!chargeEngineProperties.engineEnabled()) {
            return;
        }
        boolean pricingSomething =
                chargeEngineProperties.shadowRecording() || chargeEngineProperties.authoritative();
        if (!pricingSomething) {
            return;
        }

        long cards = chargeScheduleRepository.count();
        if (cards > 0) {
            log.info("Charges engine ready: {} rate card(s) on file, shadow-recording={}, authoritative={}",
                    cards, chargeEngineProperties.shadowRecording(), chargeEngineProperties.authoritative());
            return;
        }

        log.warn("Charges engine is switched on but NO RATE CARDS are on file. Every trade will resolve"
                        + " NO_SCHEDULE and be charged nothing{}. Run POST /charges/seed (SUPER_USER) to"
                        + " apply the shipped rate cards, then GET /charge-schedules/drift to confirm."
                        + " shadow-recording={}, authoritative={}",
                chargeEngineProperties.authoritative()
                        ? ", and that zero will become the cost basis because authoritative is on"
                        : "",
                chargeEngineProperties.shadowRecording(), chargeEngineProperties.authoritative());
    }
}
