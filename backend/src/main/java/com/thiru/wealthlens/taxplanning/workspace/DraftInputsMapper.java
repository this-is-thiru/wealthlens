package com.thiru.wealthlens.taxplanning.workspace;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.input.FairMarketValueBasis;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * A draft's inputs as text and as {@link DraftInputs}. Strict, so a misspelt field is refused rather than dropped. A
 * refusal names where, never the value, which may be an amount or an identifier ({@code DR 14} O1.1).
 */
final class DraftInputsMapper {

    static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addMixIn(FairMarketValueBasis.class, FairMarketValueBasisJson.class)
            .addModule(new SimpleModule()
                    .addKeyDeserializer(ComponentCode.class, new KeyDeserializer() {
                        @Override
                        public Object deserializeKey(String key, DeserializationContext context) {
                            return new ComponentCode(key);
                        }
                    })
                    .addKeyDeserializer(TaxYearId.class, new KeyDeserializer() {
                        @Override
                        public Object deserializeKey(String key, DeserializationContext context) {
                            return TaxYearId.of(Integer.parseInt(key));
                        }
                    }))
            .build();

    private DraftInputsMapper() {
    }

    /** The engine's sealed type, named in JSON from here because {@code taxengine} holds no JSON of its own inputs. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME)
    @JsonSubTypes({
        @JsonSubTypes.Type(FairMarketValueBasis.ListedTraded.class),
        @JsonSubTypes.Type(FairMarketValueBasis.ListedNotTraded.class),
        @JsonSubTypes.Type(FairMarketValueBasis.MerchantBankerValue.class)
    })
    private interface FairMarketValueBasisJson {
    }

    static String blank() {
        return MAPPER.writeValueAsString(DraftInputs.blank());
    }

    static JsonNode tree(String json) {
        return MAPPER.readTree(json);
    }

    static String text(JsonNode tree) {
        return MAPPER.writeValueAsString(tree);
    }

    /** The stored document, which {@link #requireBinds} already checked. */
    static DraftInputs read(String json) {
        return MAPPER.readValue(json, DraftInputs.class);
    }

    /** Refuses {@code tree} unless it binds to {@link DraftInputs}, so nothing the engine cannot read is stored. */
    static void requireBinds(JsonNode tree) {
        try {
            MAPPER.treeToValue(tree, DraftInputs.class);
        } catch (JacksonException e) {
            throw new BadRequestException("The draft's inputs do not fit at /" + e.getPath().stream()
                    .map(reference -> reference.getPropertyName() != null ? reference.getPropertyName()
                            : String.valueOf(reference.getIndex()))
                    .collect(Collectors.joining("/")));
        }
    }
}
