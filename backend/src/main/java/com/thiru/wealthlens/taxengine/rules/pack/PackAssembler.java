package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.Map;

/**
 * Turns a verified pack's bytes into a {@link RulePack}.
 *
 * <p>The seam between sourcing a pack and understanding it. A provider finds the files, reads the
 * manifest and checks that the bytes hash to what the manifest claims; it has no opinion about what
 * a rule row looks like. An assembler knows the schema and nothing about where the bytes came from.
 *
 * <p>Separating them is what lets a signed remote provider reuse the whole schema, and lets the
 * schema be tested without a filesystem.
 */
@FunctionalInterface
public interface PackAssembler {

    /**
     * @param taxYear the year being assembled
     * @param layers each layer's manifest entry, already checksum-verified
     * @param layerContents the raw JSON of each file, keyed by path relative to the pack root
     *     (e.g. {@code "law/rates.json"})
     * @return a complete pack
     * @throws com.thiru.wealthlens.taxengine.rules.validate.PackValidationException if the content does not
     *     match the schema
     */
    RulePack assemble(
            TaxYearId taxYear,
            Map<RulePackLayer, RulePackRef> layers,
            Map<String, String> layerContents);
}
