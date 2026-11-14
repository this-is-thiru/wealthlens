package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * The employer's records of official use of a vehicle (S4 r.15(3)(c); Rules 1962 r.3(2)(B); D-113): complete details
 * of each official journey and the employer's certificate that the expenditure was wholly for official duties.
 *
 * <p>With them a wholly official vehicle has no value, and a higher amount than the Table's may be claimed as
 * official use of the employee's own vehicle. Without them neither holds.
 *
 * @param journeysAndCertificateKept whether both the journey details and the certificate exist
 * @param higherOfficialUse the higher amount claimed as official use of the employee's own vehicle, if any
 */
public record OfficialUseRecord(boolean journeysAndCertificateKept, Optional<Money> higherOfficialUse) {

    public OfficialUseRecord {
        if (higherOfficialUse == null) {
            throw new IllegalArgumentException("Pass Optional.empty() where no higher official use is claimed");
        }
        if (higherOfficialUse.isPresent() && !journeysAndCertificateKept) {
            throw new IllegalArgumentException("A higher amount for official use may be claimed only where the journey"
                    + " details and the employer's certificate are kept (S4 r.15(3)(c); Rules 1962 r.3(2)(B))");
        }
        if (higherOfficialUse.filter(amount -> amount.isLessThan(Money.ZERO)).isPresent()) {
            throw new IllegalArgumentException("The official use claimed may not be negative");
        }
    }

    /** No records kept. */
    public static OfficialUseRecord none() {
        return new OfficialUseRecord(false, Optional.empty());
    }
}
