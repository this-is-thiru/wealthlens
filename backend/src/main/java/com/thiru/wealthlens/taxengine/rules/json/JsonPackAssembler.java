package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.PackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a verified pack's bytes into a {@link RulePack}.
 *
 * <p>Fills the seam {@code W-028} left: the provider found the files and proved they hash to what
 * the manifest declares; this knows the schema and nothing about where the bytes came from.
 *
 * <h2>On the mapper</h2>
 *
 * <p>{@code 19-engineering-foundations.md} E9 says to reuse {@code TJsonMapper} rather than
 * configure a second mapper, because two configurations eventually disagree about the same JSON.
 * This uses a plain tree mapper instead, and the reason is narrow: {@code TJsonMapper} binds JSON to
 * <em>types</em>, and everything here reads a {@code JsonNode} <em>tree</em>. Configuration that
 * could differ — date formats, unknown-property handling, naming strategies — applies to binding and
 * has no effect on a tree, where every value is read explicitly by {@link PackJson}. If tree access
 * is ever added to {@code TJsonMapper}, this should use it.
 */
public final class JsonPackAssembler implements PackAssembler {

    private final GoverningInstruments instruments;

    /**
     * @param instruments the three instruments for this year, resolved from the registry — the
     *     manifest carries them too, but the registry is the authority and having one source stops
     *     the two drifting
     */
    public JsonPackAssembler(GoverningInstruments instruments) {
        if (instruments == null) {
            throw new IllegalArgumentException("A pack assembler needs the year's instruments");
        }
        this.instruments = instruments;
    }

    @Override
    public RulePack assemble(
            TaxYearId taxYear,
            Map<RulePackLayer, RulePackRef> layers,
            Map<String, String> layerContents) {

        JsonNode rates = parse(layerContents, "law/rates.json");
        JsonNode regimes = parse(layerContents, "law/regimes.json");
        JsonNode components = parse(layerContents, "exec/components.json");

        return new RulePack(
                taxYear,
                layers,
                instruments,
                RuleJson.rules(rates, "law/rates.json"),
                RegistryJson.components(components, "exec/components.json"),
                RegimeJson.regimes(regimes, "law/regimes.json"),
                RegistryJson.disallowanceSets(regimes, "law/regimes.json"),
                RegistryJson.conditionSets(regimes, "law/regimes.json"));
    }

    private static JsonNode parse(Map<String, String> contents, String file) {
        String body = contents.get(file);
        if (body == null) {
            throw new PackValidationException(
                    "The pack has no " + file + "; the provider read " + contents.keySet());
        }
        try {
            return MAPPER.readTree(body);
        } catch (JacksonException e) {
            throw new PackValidationException(file + " is not valid JSON: " + e.getMessage());
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
}
