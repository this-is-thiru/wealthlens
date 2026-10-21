package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.SideVenue;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.definition.SttChargeability;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The securities transaction tax state of each side of a capital-gains bucket, as the part rows read it (D-298).
 *
 * <p>What the records show stands. Only a side whose evidence is {@code UNKNOWN} — no charge breakdown on record — is
 * put to {@code RS-STT-CHARGEABLE}, which says from the side's recorded exchange and segment whether the transaction
 * was chargeable under the Finance (No. 2) Act, 2004, Chapter VII, and from how and when the lot was acquired, since
 * Sl. 1 charges a purchase only (D-300) and Chapter VII applies from its commencement (D-301). A side no row covers stays {@code UNKNOWN}, and the part rows refuse it. Every state
 * so derived is a trace line naming its row.
 */
final class CapitalGainsStt {

    static final RuleSetId CHARGEABILITY = RuleSetIds.RS_STT_CHARGEABLE;

    private static final String UNKNOWN = "UNKNOWN";

    private static final String UNRECORDED = "UNRECORDED";

    private static final ItemFactId SIDE = new ItemFactId("stt.side");

    private static final ItemFactId ASSET = new ItemFactId("capitalgains.asset");

    private static final ItemFactId ACQUISITION = new ItemFactId("capitalgains.acquisition");

    private static final ItemFactId ACQUIRED_ON = new ItemFactId("capitalgains.acquired.on");

    private static final ItemFactId EXCHANGE = new ItemFactId("trade.exchange");

    private static final ItemFactId SEGMENT = new ItemFactId("trade.segment");

    private CapitalGainsStt() {
    }

    /** The two sides' STT codes: as recorded, or as a row of {@code RS-STT-CHARGEABLE} derives them. */
    record Sides(String acquisition, String transfer) {
    }

    /** The bucket's sides, each derived state added to {@code lines}; the term only words the trace. */
    static Sides of(NodeContext ctx, CapitalGainsInput.Bucket bucket, Term term, List<TraceLine> lines) {
        return new Sides(codeOf(ctx, bucket, term, Side.ACQUISITION, lines),
                codeOf(ctx, bucket, term, Side.TRANSFER, lines));
    }

    private static String codeOf(NodeContext ctx, CapitalGainsInput.Bucket bucket, Term term, Side side,
            List<TraceLine> lines) {
        String evidence = side.evidenceOf(bucket.facts());
        SideVenue venue = side.venueOf(bucket.facts());
        if (!UNKNOWN.equals(evidence)) {
            return evidence;
        }
        String exchange = venue.exchange().orElse(UNRECORDED);
        String segment = venue.segment().orElse(UNRECORDED);
        Optional<RuleRow> row = ctx.optionalRuleOf(CHARGEABILITY, new ItemFacts(Map.of(SIDE, side.name(), ASSET,
                bucket.facts().assetKind(), ACQUISITION, bucket.facts().acquisitionMode(), EXCHANGE, exchange, SEGMENT,
                segment), Map.of(), Map.of(ACQUIRED_ON, bucket.facts().acquiredOn())));
        if (row.isEmpty()) {
            return evidence;
        }
        String code = RuleDefinitions.require(TemporallyApportionedCap.governingVersion(row.get(),
                ctx.inputs().temporal()).definition(), SttChargeability.class, row.get().id()).code();
        lines.add(ctx.traceLine(lines.size() + 1, row.get().id(), StepKind.MEASURE, bucket.gain(), bucket.gain())
                .withLimitedBy("STT on the " + side.label + ": " + code + " (" + exchange + ", " + segment + ")"
                        + (term == Term.LONG_TERM ? "; S2 s.198 / 1961 s.112A count a chargeable side as"
                                + " paid by the owner's ruling D-294" : "")));
        return code;
    }

    /** A side of the disposal, as the rows name it. */
    private enum Side {
        ACQUISITION("buy"),
        TRANSFER("sale");

        private final String label;

        Side(String label) {
            this.label = label;
        }

        String evidenceOf(CapitalGainsInput.BucketFacts facts) {
            return this == ACQUISITION ? facts.sttOnAcquisition() : facts.sttOnTransfer();
        }

        SideVenue venueOf(CapitalGainsInput.BucketFacts facts) {
            return this == ACQUISITION ? facts.acquisitionVenue() : facts.transferVenue();
        }
    }
}
