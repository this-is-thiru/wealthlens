package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import java.util.Comparator;

/**
 * What the optimiser may put money into (W-238c): a component of the pay, the employer's contribution to a fund, or the
 * employee's own contribution above what is entered (W-238k). The input model keeps pay and the employer's contribution
 * apart — a contribution is not salary until the rows say so (S2 s.17(1)(h), D-126) — and the employee's is paid from
 * take-home, not the cost to company, so the search keeps all three apart, and only pricing writes each back where it
 * belongs.
 */
sealed interface Movable permits Movable.Pay, Movable.Fund, Movable.Contribution {

    /** Pay components first, then funds, then contributions, each by name: the order changes and ties are reported in. */
    Comparator<Movable> ORDER = Comparator.comparingInt(Movable::rank).thenComparing(Movable::name);

    String name();

    private static int rank(Movable movable) {
        return switch (movable) {
            case Pay pay -> 0;
            case Fund fund -> 1;
            case Contribution contribution -> 2;
        };
    }

    /** A component of the employment's pay. */
    record Pay(ComponentCode code) implements Movable {

        @Override
        public String name() {
            return code.value();
        }
    }

    /** The employer's contribution to a retirement fund for the employment the residual is in. */
    record Fund(EmployerFund fund) implements Movable {

        @Override
        public String name() {
            return fund.name();
        }
    }

    /**
     * What the employee adds to their own contribution {@code code} above the amount entered — never a cut, which only
     * gives up a deduction (W-238k C2a). The residual does not fund it: it is paid from take-home (C3).
     */
    record Contribution(ComponentCode code) implements Movable {

        @Override
        public String name() {
            return code.value();
        }
    }
}
