package com.thiru.wealthlens.taxengine.rules.timeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes a component into a copy of the timeline, as {@code exec/components/<CODE>.json} (D-089). The shipped
 * timeline holds none until the first rule that reads one arrives, so the reader and compiler are proven on
 * these.
 */
final class ComponentFiles {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ComponentFiles() {
    }

    /**
     * A cash salary component feeding the new regime's slab ladder — a real rule, so the file loads as a
     * shipped one would; the relation is a fixture's, not the law's.
     */
    static ObjectNode component(String code, String from, Optional<String> to) {
        ObjectNode validity = MAPPER.createObjectNode().put("from", from);
        to.ifPresent(end -> validity.put("to", end));
        ObjectNode component = MAPPER.createObjectNode()
                .put("code", code)
                .put("displayName", "Fixture " + code)
                .put("head", "SALARIES")
                .put("kind", "SALARY")
                .put("cashNature", "CASH")
                .put("treatment", "TAXABLE");
        component.putArray("feedsInto").add("RATE-SLAB-NEW-TAX-REGIME");
        component.putArray("salaryBaseFor");
        component.putObject("lifecycle")
                .<ObjectNode>set("validity", validity)
                .putObject("onWithdrawal").put("type", "HistoricalOnly");
        component.putObject("provenance")
                .put("sourceUrl", "https://egazette.gov.in/WriteReadData/2025/265620.pdf")
                .put("sourceDescription", "test fixture")
                .put("accessedOn", "2026-09-26")
                .put("verificationLevel", "SOURCE_CHECKED");
        return component;
    }

    static void write(Path root, String fileStem, ObjectNode component) throws IOException {
        Path file = root.resolve("exec/components/" + fileStem + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(component));
    }
}
