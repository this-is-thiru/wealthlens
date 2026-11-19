package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Applies a JSON Patch's {@code add}, {@code remove} and {@code replace} (RFC 6902), the operations a form sends, to a copy of a
 * document (D-441 C2a). Any other operation is refused rather than ignored.
 */
final class JsonPatchApplier {

    private JsonPatchApplier() {
    }

    /** {@code document} after {@code operations}, which is an array of patch operations; {@code document} is untouched. */
    static JsonNode apply(JsonNode document, JsonNode operations) {
        if (!operations.isArray()) {
            throw new BadRequestException("A patch is an array of operations");
        }
        JsonNode result = document.deepCopy();
        for (JsonNode operation : operations) {
            result = applyOne(result, operation);
        }
        return result;
    }

    private static JsonNode applyOne(JsonNode root, JsonNode operation) {
        String op = operation.path("op").asString("");
        List<String> tokens = tokens(operation.path("path").asString(""));
        JsonNode value = operation.get("value");
        if (!List.of("add", "remove", "replace").contains(op)) {
            throw new BadRequestException("A patch may add, remove or replace, but it asked to " + op);
        }
        if (!"remove".equals(op) && value == null) {
            throw new BadRequestException("A patch's " + op + " needs a value");
        }
        if (tokens.isEmpty()) {
            throw new BadRequestException("A patch needs a path below the document's root");
        }
        JsonNode parent = root;
        for (String token : tokens.subList(0, tokens.size() - 1)) {
            parent = child(parent, token);
        }
        write(parent, tokens.getLast(), op, value);
        return root;
    }

    private static void write(JsonNode parent, String last, String op, JsonNode value) {
        if (parent instanceof ObjectNode object) {
            if (!"add".equals(op) && !object.has(last)) {
                throw new BadRequestException("A patch's target " + last + " does not exist");
            }
            if ("remove".equals(op)) {
                object.remove(last);
            } else {
                object.set(last, value);
            }
        } else if (parent instanceof ArrayNode array) {
            writeToArray(array, last, op, value);
        } else {
            throw new BadRequestException("A patch's path passes through a value at " + last);
        }
    }

    private static void writeToArray(ArrayNode array, String last, String op, JsonNode value) {
        if ("add".equals(op) && "-".equals(last)) {
            array.add(value);
            return;
        }
        int index = index(last, "add".equals(op) ? array.size() : array.size() - 1);
        switch (op) {
            case "add" -> array.insert(index, value);
            case "remove" -> array.remove(index);
            default -> array.set(index, value);
        }
    }

    private static JsonNode child(JsonNode parent, String token) {
        JsonNode next = parent.isArray() ? parent.get(index(token, parent.size() - 1)) : parent.get(token);
        if (next == null) {
            throw new BadRequestException("A patch's path does not exist at " + token);
        }
        return next;
    }

    private static int index(String token, int highest) {
        if (!token.matches("0|[1-9][0-9]{0,8}") || Integer.parseInt(token) > highest) {
            throw new BadRequestException("A patch's array position is out of range at " + token);
        }
        return Integer.parseInt(token);
    }

    private static List<String> tokens(String path) {
        if (!path.startsWith("/")) {
            throw new BadRequestException("A patch's path starts with /");
        }
        List<String> tokens = new ArrayList<>();
        for (String raw : path.substring(1).split("/", -1)) {
            tokens.add(raw.replace("~1", "/").replace("~0", "~"));
        }
        return tokens;
    }
}
