package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import java.util.Map;

/**
 * One tax year's rule pack as compiled from the timeline: the layer files in canonical form, and a
 * checksum per rule so that a difference from the lock names the rule that moved.
 *
 * @param files each layer file's canonical content, keyed as {@link RulePackLayer#files()} names it
 * @param ruleChecksums each rule in the pack, by id, over its canonical compiled form
 */
public record CompiledPack(TaxYearId taxYear, Map<String, String> files, Map<RuleId, String> ruleChecksums) {

    public CompiledPack {
        files = Map.copyOf(files);
        ruleChecksums = Map.copyOf(ruleChecksums);
    }

    /** The layer's checksum: its files' content in {@link RulePackLayer#files()} order, as the loader hashes it. */
    public String layerChecksum(RulePackLayer layer) {
        StringBuilder content = new StringBuilder();
        for (String file : layer.files()) {
            content.append(files.get(file));
        }
        return RulePackRef.checksumOf(content.toString());
    }
}
