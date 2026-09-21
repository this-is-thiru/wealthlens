package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxrules.money.Money;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tax, split by the basis it was charged on: the slab ladder, and each special rate separately.
 *
 * <h2>Why this is not one number</h2>
 *
 * <p>{@code 13-edge-cases.md} R-5. The rebate is capped by reference to tax at <b>slab rates
 * alone</b>. Compute it on the total instead and a taxpayer with &#8377;12,00,000 of income that is
 * mostly capital gains gets a nil tax bill, which they do not have. The figure looks right, the
 * arithmetic is sound at every step, and the answer is wrong by the whole bill.
 *
 * <p>The surcharge needs the opposite: {@code RATE-SUR-SPLIT-001} caps the special-rate slice at
 * fifteen per cent while the slab slice takes the full ladder, so it reads both parts and must be
 * able to tell them apart.
 *
 * <p>So the partition travels as a value. A node that flattens it has to do so deliberately, by
 * calling {@link #asMoney()}, rather than by receiving a {@code Money} that lost the distinction
 * somewhere upstream.
 *
 * @param atSlabRates tax charged through the slab ladder
 * @param atSpecialRates tax charged at each special rate, kept separate
 */
public record TaxByBasis(Money atSlabRates, Map<SpecialRateId, Money> atSpecialRates)
        implements NodeValue {

    public TaxByBasis {
        if (atSlabRates == null) {
            throw new IllegalArgumentException("TaxByBasis must state the tax at slab rates");
        }
        if (atSpecialRates == null) {
            throw new IllegalArgumentException(
                    "TaxByBasis special rates may not be null; pass an empty map where there are none");
        }
        atSpecialRates = Map.copyOf(atSpecialRates);
    }

    /** Slab tax only — the ordinary case, before any special-rate income arrives. */
    public static TaxByBasis atSlabRatesOnly(Money atSlabRates) {
        return new TaxByBasis(atSlabRates, Map.of());
    }

    /**
     * The total, for the trace's summary column.
     *
     * <p>Whoever calls this is discarding the partition. That is right for rendering and wrong for
     * the rebate — see the class note.
     */
    @Override
    public Money asMoney() {
        return atSpecialRates.values().stream().reduce(atSlabRates, Money::plus);
    }

    /** Tax at one special rate, or zero where none was charged at it. */
    public Money at(SpecialRateId rate) {
        return atSpecialRates.getOrDefault(rate, Money.ZERO);
    }

    /** The sum of every special-rate charge, which the surcharge split caps separately. */
    public Money totalAtSpecialRates() {
        return atSpecialRates.values().stream().reduce(Money.ZERO, Money::plus);
    }

    /** The same partition with one special rate's tax added. */
    public TaxByBasis plusSpecialRate(SpecialRateId rate, Money tax) {
        Map<SpecialRateId, Money> merged = new LinkedHashMap<>(atSpecialRates);
        merged.merge(rate, tax, Money::plus);
        return new TaxByBasis(atSlabRates, merged);
    }
}
