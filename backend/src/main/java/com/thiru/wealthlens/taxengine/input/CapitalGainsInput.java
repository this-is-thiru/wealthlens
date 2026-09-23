package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.component.RateBasisHint;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What the portfolio says about capital gains, pinned.
 *
 * <h2>Gross buckets, never tax</h2>
 *
 * <p>{@code 13-edge-cases.md} R-10 and {@code 15-review-findings.md} F5. {@code portfolio} owns
 * which disposals happened, their dates, the FIFO cost basis and the amount of gain; the tax side
 * owns the rate basis, ss.196/197/198, the surcharge split and s.108(2) set-off. So every figure
 * here is a <b>gross amount with a hint</b>, and no figure here is a tax.
 *
 * <p>Brought-forward losses arrive <b>un-netted</b>, per origin year and per term (F5). Netting
 * them early would destroy the asymmetry s.108(2) depends on — see {@link Term}.
 *
 * <h2>Pinned, not live</h2>
 *
 * <p>{@code 11-filing-drafts.md} F4.1. A draft computed in September and reopened in January must
 * not silently change because the portfolio moved, so a computation pins the summary it ran
 * against. {@link #asOf} and {@link #portfolioComputationRef} are what make that pin checkable
 * later; the "portfolio has changed since you last computed" prompt is a comparison of two
 * {@code asOf}s, not a recomputation.
 *
 * <p>A {@code taxengine}-owned projection of {@code portfolio}'s published read-model (decision
 * <b>D-024</b>). It carries no {@code CapitalGainsType}: that enum is {@code portfolio}'s, the
 * engine may not see it, and by the time a bucket reaches here {@code taxplanning} has already
 * decided whether the class counts as capital gains — {@code UNCLASSIFIED} is a blocking error
 * upstream ({@code X1.3}), not something the engine reasons about.
 *
 * @param buckets gross gain per bucket; may be empty, never null
 * @param broughtForward un-netted losses by origin year and term
 * @param asOf when the portfolio computed this
 * @param portfolioComputationRef the portfolio's own reference for the run, so a pinned figure can
 *     be traced back to what produced it
 */
public record CapitalGainsInput(
        List<Bucket> buckets,
        List<BroughtForwardLoss> broughtForward,
        Instant asOf,
        String portfolioComputationRef) {

    /**
     * Long-term or short-term.
     *
     * <p>The engine needs this and cannot derive it, because {@code SETOFF-INTRA-CG-001}
     * ({@code 01-rulebook.md:952}, s.108(2)(a),(b)) is <b>asymmetric</b>: a short-term loss may be
     * set against income from <i>any</i> other capital asset, a long-term loss <i>only</i> against
     * long-term capital gains. Lose the term and the set-off silently over-allows.
     */
    public enum Term {
        SHORT_TERM,
        LONG_TERM
    }

    /**
     * One bucket of gross gain.
     *
     * @param gain the gross amount; never a tax
     * @param term long or short, for s.108(2)
     * @param basis which rate basis applies — an unrecognised one is a blocking error, never a
     *     fallback to slab (R-10)
     * @param portfolioRuleRef the portfolio's own rule reference, where it has one
     */
    public record Bucket(
            Money gain, Term term, RateBasisHint basis, Optional<String> portfolioRuleRef) {

        public Bucket {
            if (gain == null) {
                throw new IllegalArgumentException("A capital-gains bucket must carry a gain");
            }
            if (term == null) {
                throw new IllegalArgumentException(
                        "A capital-gains bucket must state its term; s.108(2) sets off short-term"
                                + " and long-term losses differently");
            }
            if (basis == null) {
                throw new IllegalArgumentException(
                        "A capital-gains bucket must state a rate basis; an absent basis is a"
                                + " blocking error, not a fallback to the slab ladder");
            }
            if (portfolioRuleRef == null) {
                throw new IllegalArgumentException(
                        "Bucket.portfolioRuleRef may not be null; pass Optional.empty()");
            }
        }
    }

    /**
     * A loss carried in from an earlier year, as the portfolio holds it.
     *
     * @param originYear the year the loss arose, which is what makes the expiry computable
     * @param term long or short — the two are not interchangeable under s.108(2)
     * @param amount the un-netted amount
     */
    public record BroughtForwardLoss(TaxYearId originYear, Term term, Money amount) {

        public BroughtForwardLoss {
            if (originYear == null) {
                throw new IllegalArgumentException(
                        "A brought-forward loss must name the year it arose; the carry-forward"
                                + " window is counted from it");
            }
            if (term == null) {
                throw new IllegalArgumentException(
                        "A brought-forward loss must state its term; s.108(2) is asymmetric");
            }
            if (amount == null) {
                throw new IllegalArgumentException("A brought-forward loss must carry an amount");
            }
        }
    }

    public CapitalGainsInput {
        if (buckets == null || broughtForward == null) {
            throw new IllegalArgumentException(
                    "CapitalGainsInput lists may not be null; pass empty lists for a taxpayer with"
                            + " no capital gains");
        }
        if (asOf == null) {
            throw new IllegalArgumentException(
                    "CapitalGainsInput must carry an asOf; without it a pinned summary cannot be"
                            + " compared against a newer one (F4.1)");
        }
        if (portfolioComputationRef == null || portfolioComputationRef.isBlank()) {
            throw new IllegalArgumentException(
                    "CapitalGainsInput must carry the portfolio's computation reference, so a"
                            + " pinned figure can be traced to what produced it");
        }
        buckets = List.copyOf(buckets);
        broughtForward = List.copyOf(broughtForward);
    }

    /** No capital gains at all, pinned to a run that found none. */
    public static CapitalGainsInput none(Instant asOf, String portfolioComputationRef) {
        return new CapitalGainsInput(List.of(), List.of(), asOf, portfolioComputationRef);
    }

    /** Gross gain on one rate basis — the input to the surcharge split. */
    public Money grossGainOn(RateBasisHint basis) {
        return buckets.stream()
                .filter(b -> b.basis() == basis)
                .map(Bucket::gain)
                .reduce(Money.ZERO, Money::plus);
    }

    /** Un-netted brought-forward losses of one term — what s.108(2) may set off, and against what. */
    public Money broughtForwardLossOf(Term term) {
        return broughtForward.stream()
                .filter(l -> l.term() == term)
                .map(BroughtForwardLoss::amount)
                .reduce(Money.ZERO, Money::plus);
    }
}
