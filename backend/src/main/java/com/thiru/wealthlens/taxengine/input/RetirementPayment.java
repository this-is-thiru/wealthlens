package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * A retirement or termination payment and the entry of the Acts it falls under (S2 s.19(1) Table; 1961
 * s.10(10)–(10C); D-103).
 *
 * <p>The amount is the component's, as paid with the salary; this record adds what the amount cannot say. Two
 * entries read one more fact each: commuted pension from any other employer is limited to a fraction of the whole
 * pension's commuted value — an actuarial figure the Acts leave to the employer's tables — and voluntary retirement
 * is refused where relief for arrears was allowed on the amount (s.19(2)(e)(iii); 1961 s.10(10C), s.89). Other
 * gratuity reads the separation it is measured on, and other leave encashment the employer's leave figures (D-106).
 * A fact given for an entry that does not read it is refused, since it would be ignored.
 *
 * @param component the pay component the payment was entered as
 * @param entry the entry it falls under
 * @param commutedValueOfWholePension the commuted value of the whole pension, for an other employer's commutation
 * @param arrearsReliefAllowed whether relief for arrears was allowed on the amount, for voluntary retirement
 * @param separation the service and the ten months before the event, for other gratuity
 * @param leave the employer's leave figures, for other leave encashment
 */
public record RetirementPayment(
        ComponentCode component,
        RetirementEntry entry,
        Optional<Money> commutedValueOfWholePension,
        boolean arrearsReliefAllowed,
        Optional<Separation> separation,
        Optional<LeaveAtRetirement> leave) {

    public RetirementPayment {
        if (component == null || entry == null || commutedValueOfWholePension == null || separation == null
                || leave == null) {
            throw new IllegalArgumentException("A retirement payment needs its component and entry; pass"
                    + " Optional.empty() for a fact not given");
        }
        requireExactlyFor(component, entry, RetirementEntry.OTHER_EMPLOYER_COMMUTATION,
                commutedValueOfWholePension.isPresent(), "commuted value of the whole pension");
        if (commutedValueOfWholePension.filter(value -> !value.isGreaterThan(Money.ZERO)).isPresent()) {
            throw new IllegalArgumentException(component + ": the commuted value of the whole pension must be"
                    + " positive");
        }
        requireExactlyFor(component, entry, RetirementEntry.OTHER_GRATUITY, separation.isPresent(), "separation");
        requireExactlyFor(component, entry, RetirementEntry.OTHER_LEAVE_ENCASHMENT, leave.isPresent(),
                "leave figures");
        if (arrearsReliefAllowed && entry != RetirementEntry.VOLUNTARY_RETIREMENT) {
            throw new IllegalArgumentException(component + ": relief for arrears bars only a voluntary retirement"
                    + " deduction, not " + entry);
        }
    }

    private static void requireExactlyFor(ComponentCode component, RetirementEntry entry, RetirementEntry readBy,
            boolean given, String fact) {
        if (entry == readBy && !given) {
            throw new IllegalArgumentException(component + ": " + readBy + " is measured on the " + fact
                    + "; enter it");
        }
        if (entry != readBy && given) {
            throw new IllegalArgumentException(component + ": " + entry + " does not read a " + fact
                    + "; it would be ignored, so it is refused");
        }
    }

    /** A payment whose entry reads nothing beyond the amount. */
    public static RetirementPayment of(ComponentCode component, RetirementEntry entry) {
        return new RetirementPayment(component, entry, Optional.empty(), false, Optional.empty(), Optional.empty());
    }

    /** Any other gratuity, with the separation it is measured on. */
    public static RetirementPayment otherGratuity(ComponentCode component, Separation separation) {
        return new RetirementPayment(component, RetirementEntry.OTHER_GRATUITY, Optional.empty(), false,
                Optional.ofNullable(separation), Optional.empty());
    }

    /** Other leave encashment, with the employer's leave figures. */
    public static RetirementPayment otherLeaveEncashment(ComponentCode component, LeaveAtRetirement leave) {
        return new RetirementPayment(component, RetirementEntry.OTHER_LEAVE_ENCASHMENT, Optional.empty(), false,
                Optional.empty(), Optional.ofNullable(leave));
    }

    /** Commuted pension from any other employer, with the commuted value of the whole pension. */
    public static RetirementPayment otherEmployerCommutation(ComponentCode component, Money commutedValue) {
        return new RetirementPayment(component, RetirementEntry.OTHER_EMPLOYER_COMMUTATION,
                Optional.ofNullable(commutedValue), false, Optional.empty(), Optional.empty());
    }

    /** An amount on voluntary retirement, and whether relief for arrears was allowed on it. */
    public static RetirementPayment voluntaryRetirement(ComponentCode component, boolean arrearsReliefAllowed) {
        return new RetirementPayment(component, RetirementEntry.VOLUNTARY_RETIREMENT, Optional.empty(),
                arrearsReliefAllowed, Optional.empty(), Optional.empty());
    }
}
