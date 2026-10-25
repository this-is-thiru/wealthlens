package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.Instant;
import java.time.LocalDate;
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
 * here is a <b>gross amount with the facts the law turns on</b>, and no figure here is a tax: which part of the head a
 * bucket falls in is the rule data's (D-289).
 *
 * <p>Losses carried in from earlier years are not here: what earlier returns absorbed is the tax side's history,
 * held in {@link LedgerSnapshot#capitalLossCarriedForward} (D-247).
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
 * <b>D-024</b>). It carries facts only: whether the capital-gains head takes a bucket, and its
 * term, are the engine's rule data (W-160a, D-363; D-305), and {@code portfolio} no longer
 * classifies a disposal at all (W-160b – f).
 *
 * @param buckets gross gain per bucket; may be empty, never null
 * @param asOf when the portfolio computed this
 * @param portfolioComputationRef the portfolio's own reference for the run, so a pinned figure can
 *     be traced back to what produced it
 */
public record CapitalGainsInput(
        List<Bucket> buckets,
        Instant asOf,
        String portfolioComputationRef) {

    /**
     * Long-term or short-term.
     *
     * <p>The engine derives it per bucket from the bucket's dates and rule set {@code RS-CG-TERM} (1961 s.2(42A); 2025
     * s.2(101); W-088k3, D-305) — never taken from {@code portfolio}. It matters twice: the part rows test it, and
     * {@code SETOFF-INTRA-CG-001} (s.108(2)(a),(b)) is <b>asymmetric</b> — a short-term loss may be set against income
     * from <i>any</i> other capital asset, a long-term loss <i>only</i> against long-term capital gains.
     */
    public enum Term {
        SHORT_TERM,
        LONG_TERM
    }

    /**
     * One bucket of gross gain.
     *
     * @param gain the gross amount; never a tax
     * @param facts what the special-rate provisions turn on; a bucket no rule covers is a blocking error, never a
     *     fallback to slab (R-10)
     * @param portfolioRuleRef the portfolio's own rule reference, where it has one
     */
    public record Bucket(
            Money gain, BucketFacts facts, Optional<String> portfolioRuleRef) {

        public Bucket {
            if (gain == null) {
                throw new IllegalArgumentException("A capital-gains bucket must carry a gain");
            }
            if (facts == null) {
                throw new IllegalArgumentException(
                        "A capital-gains bucket must carry its facts; without them no rule can place it, and an"
                                + " unplaced bucket is a blocking error, not a fallback to the slab ladder");
            }
            if (portfolioRuleRef == null) {
                throw new IllegalArgumentException(
                        "Bucket.portfolioRuleRef may not be null; pass Optional.empty()");
            }
        }
    }

    /**
     * The facts of a bucket the special-rate provisions turn on, as {@code portfolio} recorded them (D-249, D-289).
     * Categories are {@code portfolio}'s own names, passed through: the engine may not see that module's enums, and a
     * kind it learns later must reach the rule data unchanged rather than be collapsed here.
     *
     * @param assetKind the kind of asset, such as {@code EQUITY}
     * @param sttOnAcquisition what the records say of securities transaction tax on the buy, such as {@code PAID},
     *     {@code NOT_PAID} or {@code UNKNOWN}
     * @param sttOnTransfer what they say of it on the sale
     * @param acquisitionMode how the lot was acquired, such as {@code PURCHASE} or {@code CORPORATE_ACTION}
     * @param corporateAction the corporate action that created the lot; present exactly when the mode is
     *     {@code CORPORATE_ACTION}
     * @param acquiredOn when the lot was acquired
     * @param transferredOn when the disposals were made — with {@code acquiredOn}, what the term is counted from and
     *     to
     * @param acquisitionVenue where and how the buy was traded, as recorded — what decides whether its STT was
     *     chargeable where the evidence is unknown (D-298)
     * @param transferVenue where and how the sale was traded, as recorded
     */
    public record BucketFacts(
            String assetKind,
            String sttOnAcquisition,
            String sttOnTransfer,
            String acquisitionMode,
            Optional<String> corporateAction,
            LocalDate acquiredOn,
            LocalDate transferredOn,
            SideVenue acquisitionVenue,
            SideVenue transferVenue) {

        private static final String CORPORATE_ACTION = "CORPORATE_ACTION";

        public BucketFacts {
            if (isBlank(assetKind) || isBlank(sttOnAcquisition) || isBlank(sttOnTransfer) || isBlank(acquisitionMode)
                    || corporateAction == null || acquiredOn == null || transferredOn == null
                    || acquisitionVenue == null || transferVenue == null) {
                throw new IllegalArgumentException("A bucket's facts need its asset kind, STT evidence on both sides,"
                        + " acquisition mode, acquisition and transfer dates and both venues; pass Optional.empty() for"
                        + " no corporate action and SideVenue.UNRECORDED for no venue");
            }
            if (corporateAction.isPresent() != CORPORATE_ACTION.equals(acquisitionMode)) {
                throw new IllegalArgumentException("A corporate action is named exactly when the lot was acquired by"
                        + " one, but the mode was " + acquisitionMode + " and the corporate action " + corporateAction);
            }
        }

        private static boolean isBlank(String code) {
            return code == null || code.isBlank();
        }
    }

    /**
     * Where and how one side of a disposal was traded, as {@code portfolio} recorded it: the exchange string and the
     * segment's name, passed as is (D-295, D-298). Which exchanges are recognised is the rule data's.
     *
     * @param exchange the exchange the side was recorded on; empty where not recorded
     * @param segment the segment it was recorded in, such as {@code DELIVERY}; empty where not recorded
     */
    public record SideVenue(Optional<String> exchange, Optional<String> segment) {

        /** A side whose record says nothing of where or how it was traded. */
        public static final SideVenue UNRECORDED = new SideVenue(Optional.empty(), Optional.empty());

        public SideVenue {
            if (exchange == null || segment == null) {
                throw new IllegalArgumentException("A side venue needs its exchange and segment; pass Optional.empty()"
                        + " for one not recorded");
            }
        }
    }

    public CapitalGainsInput {
        if (buckets == null) {
            throw new IllegalArgumentException(
                    "CapitalGainsInput buckets may not be null; pass an empty list for a taxpayer with"
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
    }

    /** No capital gains at all, pinned to a run that found none. */
    public static CapitalGainsInput none(Instant asOf, String portfolioComputationRef) {
        return new CapitalGainsInput(List.of(), asOf, portfolioComputationRef);
    }
}
