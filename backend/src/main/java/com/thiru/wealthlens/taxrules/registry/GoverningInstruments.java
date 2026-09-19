package com.thiru.wealthlens.taxrules.registry;

import java.util.List;

/**
 * The three instruments that govern one tax year.
 *
 * <p>Three, not one. The rates live in the Finance Act, the machinery in the Act, and the values a
 * salaried taxpayer actually meets — the metro list for house rent allowance, the allowance caps,
 * the perquisite valuations — in the Rules. Citing only the Act would attribute a Rules figure to
 * the wrong instrument, which is how a value notified for one year gets labelled with another.
 */
public record GoverningInstruments(Instrument act, Instrument financeAct, Instrument rules) {

    public GoverningInstruments {
        require(act, "act");
        require(financeAct, "financeAct");
        require(rules, "rules");
    }

    private static void require(Instrument instrument, String name) {
        if (instrument == null) {
            throw new IllegalArgumentException("GoverningInstruments." + name + " may not be null");
        }
    }

    public List<Instrument> all() {
        return List.of(act, financeAct, rules);
    }

    /** Whether any governing instrument is itself unverified, which the user should be told. */
    public boolean anyUnverified() {
        return all().stream().anyMatch(i -> i.status() != VerificationStatus.VERIFIED);
    }
}
