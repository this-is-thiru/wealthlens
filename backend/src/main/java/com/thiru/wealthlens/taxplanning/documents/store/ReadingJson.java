package com.thiru.wealthlens.taxplanning.documents.store;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.FieldLocation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The stored text of what a document was read to say (W-131a, D-761 C3a). Its own shape rather than a mapper's view of the
 * records: each value names its kind, since a reader cannot tell one from another otherwise, and an amount is exact text, so
 * a stored row does not change when a class's accessors do. A kind this code never wrote is refused, never guessed.
 */
final class ReadingJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ReadingJson() {
    }

    static String writeFields(List<ExtractedField> fields) {
        ArrayNode array = MAPPER.createArrayNode();
        for (ExtractedField field : fields) {
            ObjectNode node = array.addObject();
            node.put("path", field.fieldPath());
            field.rawText().ifPresent(text -> node.put("rawText", text));
            field.interpretedValue().ifPresent(value -> node.set("value", value(value)));
            node.put("confidence", field.confidence());
            field.location().ifPresent(location -> node.set("location", location(location)));
            field.mappedTo().ifPresent(code -> node.put("mappedTo", code.value()));
        }
        return MAPPER.writeValueAsString(array);
    }

    static List<ExtractedField> readFields(String stored) {
        List<ExtractedField> fields = new ArrayList<>();
        for (JsonNode node : MAPPER.readTree(stored)) {
            fields.add(new ExtractedField(node.get("path").asString(), optional(node, "rawText").map(JsonNode::asString),
                    optional(node, "value").map(ReadingJson::value), node.get("confidence").asDouble(),
                    optional(node, "location").map(ReadingJson::location),
                    optional(node, "mappedTo").map(code -> new ComponentCode(code.asString()))));
        }
        return fields;
    }

    static String writeValues(Map<String, ExtractedValue> values) {
        ObjectNode object = MAPPER.createObjectNode();
        values.forEach((path, value) -> object.set(path, value(value)));
        return MAPPER.writeValueAsString(object);
    }

    static Map<String, ExtractedValue> readValues(String stored) {
        Map<String, ExtractedValue> values = new LinkedHashMap<>();
        MAPPER.readTree(stored).properties().forEach(entry -> values.put(entry.getKey(), value(entry.getValue())));
        return values;
    }

    static String writeTexts(List<String> texts) {
        ArrayNode array = MAPPER.createArrayNode();
        texts.forEach(array::add);
        return MAPPER.writeValueAsString(array);
    }

    static List<String> readTexts(String stored) {
        List<String> texts = new ArrayList<>();
        MAPPER.readTree(stored).forEach(node -> texts.add(node.asString()));
        return texts;
    }

    private static ObjectNode value(ExtractedValue value) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (value) {
            case ExtractedValue.MoneyRead money -> node.put("kind", "MONEY").put("amount", money.amount().toString());
            case ExtractedValue.FlagRead flag -> node.put("kind", "FLAG").put("value", flag.value());
            case ExtractedValue.PeriodRead period -> node.put("kind", "PERIOD").put("from", period.from().toString())
                    .put("to", period.to().toString());
            case ExtractedValue.TextRead text -> node.put("kind", "TEXT").put("text", text.text());
        }
        return node;
    }

    private static ExtractedValue value(JsonNode node) {
        String kind = node.get("kind").asString();
        return switch (kind) {
            case "MONEY" -> new ExtractedValue.MoneyRead(Money.of(node.get("amount").asString()));
            case "FLAG" -> new ExtractedValue.FlagRead(node.get("value").asBoolean());
            case "PERIOD" -> new ExtractedValue.PeriodRead(LocalDate.parse(node.get("from").asString()),
                    LocalDate.parse(node.get("to").asString()));
            case "TEXT" -> new ExtractedValue.TextRead(node.get("text").asString());
            default -> throw new IllegalStateException("A stored value of kind " + kind + " cannot be read");
        };
    }

    private static ObjectNode location(FieldLocation location) {
        return MAPPER.createObjectNode().put("page", location.pageNumber()).put("left", location.left()).put("top", location.top())
                .put("width", location.width()).put("height", location.height());
    }

    private static FieldLocation location(JsonNode node) {
        return new FieldLocation(node.get("page").asInt(), node.get("left").asDouble(), node.get("top").asDouble(),
                node.get("width").asDouble(), node.get("height").asDouble());
    }

    private static Optional<JsonNode> optional(JsonNode node, String name) {
        return Optional.ofNullable(node.get(name));
    }
}
