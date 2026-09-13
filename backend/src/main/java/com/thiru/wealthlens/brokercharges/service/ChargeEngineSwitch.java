package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.config.ChargeEngineProperties;
import com.thiru.wealthlens.shared.exception.ServiceUnavailableException;

/**
 * The charges engine's master switch, {@code app.charges.engine-enabled}.
 *
 * <p>It outranks every phase flag. An operator switching the engine off mid-incident should not also
 * have to find and clear {@code shadow-recording} — one flag stops everything, which is the only
 * property that makes a kill switch usable when it is needed.
 *
 * <p><b>The check belongs at the entry points, not inside {@code ChargeEngine}.</b> Returning an
 * empty computation from the engine would be indistinguishable from "no rate card on file", and
 * {@code UserChargeService} would dutifully <em>record</em> it — so switching the engine off would
 * quietly fill the database with rows that read as gaps, and the gaps report would blame the seed
 * data. A disabled engine has to write nothing at all.
 *
 * <p>The one caller that must not throw is {@code ChargeRecordingGatewayImpl}: it sits in the trade
 * path, and a trade must still save. It checks the flag itself and records nothing.
 */
final class ChargeEngineSwitch {

    private static final String DISABLED =
            "The charges engine is disabled (app.charges.engine-enabled=false); no charge was computed or recorded";

    private ChargeEngineSwitch() {
    }

    static void requireEnabled(ChargeEngineProperties properties) {
        if (!properties.engineEnabled()) {
            throw new ServiceUnavailableException(DISABLED);
        }
    }
}
