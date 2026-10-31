package com.thiru.wealthlens.taxengine.test;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.node.CapitalGainsNode;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Runs {@link CapitalGainsNode} on the shipped pack of a year for a plain taxpayer, for tests outside
 * {@code CapitalGainsNodeTest} that put buckets to the engine — the charge rows (W-162a) and the path from
 * {@code portfolio}'s summary.
 */
public final class CapitalGainsHarness {

    private CapitalGainsHarness() {
    }

    /** The node's result for {@code input} in the tax year starting in {@code year}. */
    public static NodeResult run(int year, CapitalGainsInput input) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider("rulepacks", Set.of(taxYear),
                new JsonPackAssembler(LockedInstruments.of(taxYear))).load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, plain.regime(),
                TemporalContext.forTaxYear(taxYear, taxYear.startsOn().plusMonths(5)), plain.entered(),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), input, plain.filing());
        return NodeHarness.with(pack).regime(plain.regime()).inputs(inputs).run(new CapitalGainsNode());
    }

    /** {@code buckets} as one run's input. */
    public static CapitalGainsInput inputOf(CapitalGainsInput.Bucket... buckets) {
        return new CapitalGainsInput(List.of(buckets), Instant.parse("2026-04-15T10:00:00Z"), "run-1");
    }
}
