package com.thiru.wealthlens.taxengine.optimise;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import java.util.Comparator;

/**
 * What the optimiser may put money into (W-238c): a component of the pay, or the employer's contribution to a fund. The
 * input model keeps the two apart — a contribution is not salary until the rows say so (S2 s.17(1)(h), D-126) — so the
 * search does too, and only pricing writes each back where it belongs.
 */
sealed interface Movable permits Movable.Pay, Movable.Fund {

    /** Pay components first, then funds, each by name: the order changes and ties are reported in. */
    Comparator<Movable> ORDER = Comparator.comparing((Movable movable) -> movable instanceof Fund)
            .thenComparing(Movable::name);

    String name();

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
}
