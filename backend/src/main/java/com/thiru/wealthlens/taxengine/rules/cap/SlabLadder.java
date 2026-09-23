package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.List;
import java.util.Optional;

/**
 * A progressive rate table: the slab ladder, and the surcharge ladders that share its shape.
 *
 * <p>Each band taxes only the slice of income that falls inside it, so the ladder is a sum over
 * bands rather than a rate applied to the whole. {@code RATE-SLAB-NEW-2026} at &#8377;8,00,000 is
 * &#8377;20,000 — five per cent of the second band's four lakh — not five per cent of eight lakh.
 *
 * <h2>The one-rupee trap</h2>
 *
 * <p>The statute writes the second band as <em>&ldquo;From &#8377;400001 to &#8377;800000&rdquo;</em>
 * and the old regime writes the same partition as <em>&ldquo;exceeds &#8377;2,50,000 but does not
 * exceed &#8377;5,00,000&rdquo;</em>. Both describe the same half-open partition of the rupee line,
 * and a band's taxable width is {@code min(income, upper) - lower} where {@code lower} is the
 * <b>previous band's upper</b> — &#8377;4,00,000, not &#8377;4,00,001.
 *
 * <p>So {@link Band#lowerExclusive} is the arithmetic lower bound, used as written. Storing
 * {@code lower + 1} loses one rupee of taxable width per band, and a ladder holding {@code 400001}
 * is rejected by {@link #validate()} when the pack is read — not caught later by a test, and never
 * silently absorbed. That is {@code RATE-SLAB-BAND-001}/{@code -002}, and hypothesis H-5's first
 * limb.
 *
 * @param bands in ascending order, partitioning the rupee line from zero upward
 * @param base which amount the ladder is applied to
 */
public record SlabLadder(List<Band> bands, BaseId base) implements CapShape {

    /**
     * One band of the ladder.
     *
     * @param lowerExclusive the previous band's ceiling, and this band's arithmetic lower bound
     * @param upperInclusive this band's ceiling; empty for the open top band
     * @param rate the rate on income falling inside this band
     */
    public record Band(Money lowerExclusive, Optional<Money> upperInclusive, Rate rate) {

        public Band {
            if (lowerExclusive == null || upperInclusive == null || rate == null) {
                throw new IllegalArgumentException("A band's bounds and rate may not be null");
            }
        }

        boolean isOpen() {
            return upperInclusive.isEmpty();
        }
    }

    public SlabLadder {
        if (bands == null) {
            throw new IllegalArgumentException("A slab ladder's bands may not be null");
        }
        if (base == null) {
            throw new IllegalArgumentException("A slab ladder's base may not be null");
        }
        bands = List.copyOf(bands);
    }

    /** Sums the tax on each band's slice. No rounding here — {@code RATE-ROUND-003}. */
    @Override
    public CapResult apply(CapContext ctx) {
        Money income = ctx.base(base);
        Money tax = Money.ZERO;
        for (Band band : bands) {
            if (income.isLessThanOrEqual(band.lowerExclusive())) {
                break;
            }
            Money ceiling = band.upperInclusive().orElse(income);
            Money width = Money.min(income, ceiling).minus(band.lowerExclusive());
            tax = tax.plus(band.rate().of(width));
        }
        return new CapResult(tax, shapeName());
    }

    @Override
    public String shapeName() {
        return "slab";
    }

    /**
     * Load-time guard: the bands must partition the rupee line with no gap and no overlap.
     *
     * @throws PackValidationException naming the offending band, so a pack author can find it
     */
    public void validate() {
        if (bands.isEmpty()) {
            throw new PackValidationException("A slab ladder must have at least one band");
        }
        if (!bands.getFirst().lowerExclusive().equals(Money.ZERO)) {
            throw new PackValidationException(
                    "band 0 starts at " + bands.getFirst().lowerExclusive()
                            + "; the first band must start at 0, or income below it is untaxed");
        }
        for (int i = 0; i < bands.size() - 1; i++) {
            if (bands.get(i).isOpen()) {
                throw new PackValidationException(
                        "band " + i + " is open-ended but is not the last band; every band above it is unreachable");
            }
        }
        if (!bands.getLast().isOpen()) {
            throw new PackValidationException(
                    "the last band ends at " + bands.getLast().upperInclusive().orElseThrow()
                            + "; the top band must be open-ended, or income above it is untaxed");
        }
        for (int i = 1; i < bands.size(); i++) {
            Money previousTop = bands.get(i - 1).upperInclusive().orElseThrow();
            Money lower = bands.get(i).lowerExclusive();
            if (!lower.equals(previousTop)) {
                throw new PackValidationException(
                        "band " + i + " lowerExclusive " + lower + " != band " + (i - 1)
                                + " upperInclusive " + previousTop
                                + "; RATE-SLAB-BAND-002: the arithmetic lower bound IS the previous ceiling,"
                                + " so a band written as the previous ceiling plus one rupee is wrong");
            }
        }
    }
}
