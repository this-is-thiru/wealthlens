package com.thiru.wealthlens.taxplanning.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

class JsonPatchApplierTest {

    private static final String DOCUMENT = "{\"a\": 1, \"list\": [10, 20], \"x/y\": 2}";

    private static String applied(String patch) {
        return DraftInputsMapper.text(JsonPatchApplier.apply(DraftInputsMapper.tree(DOCUMENT), DraftInputsMapper.tree(patch)));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "[{\"op\":\"replace\",\"path\":\"/a\",\"value\":5}]|{\"a\":5,\"list\":[10,20],\"x/y\":2}",
        "[{\"op\":\"add\",\"path\":\"/b\",\"value\":5}]|{\"a\":1,\"list\":[10,20],\"x/y\":2,\"b\":5}",
        "[{\"op\":\"remove\",\"path\":\"/a\"}]|{\"list\":[10,20],\"x/y\":2}",
        "[{\"op\":\"add\",\"path\":\"/list/-\",\"value\":30}]|{\"a\":1,\"list\":[10,20,30],\"x/y\":2}",
        "[{\"op\":\"add\",\"path\":\"/list/1\",\"value\":15}]|{\"a\":1,\"list\":[10,15,20],\"x/y\":2}",
        "[{\"op\":\"remove\",\"path\":\"/list/0\"}]|{\"a\":1,\"list\":[20],\"x/y\":2}",
        "[{\"op\":\"replace\",\"path\":\"/list/1\",\"value\":25}]|{\"a\":1,\"list\":[10,25],\"x/y\":2}",
        "[{\"op\":\"replace\",\"path\":\"/x~1y\",\"value\":3}]|{\"a\":1,\"list\":[10,20],\"x/y\":3}"
    })
    void apply_whenTheOperationIsSupported_changesOnlyItsTarget(String patch, String expected) {
        // When / Then
        assertThat(applied(patch)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"op\":\"remove\",\"path\":\"/a\"}",
        "[{\"op\":\"move\",\"from\":\"/a\",\"path\":\"/b\"}]",
        "[{\"op\":\"test\",\"path\":\"/a\",\"value\":1}]",
        "[{\"op\":\"replace\",\"path\":\"/absent\",\"value\":1}]",
        "[{\"op\":\"remove\",\"path\":\"/absent\"}]",
        "[{\"op\":\"replace\",\"path\":\"/a\"}]",
        "[{\"op\":\"add\",\"path\":\"/absent/b\",\"value\":1}]",
        "[{\"op\":\"remove\",\"path\":\"/list/2\"}]",
        "[{\"op\":\"add\",\"path\":\"/list/3\",\"value\":1}]",
        "[{\"op\":\"add\",\"path\":\"/a/b\",\"value\":1}]",
        "[{\"op\":\"replace\",\"path\":\"\",\"value\":1}]",
        "[{\"op\":\"replace\",\"path\":\"a\",\"value\":1}]"
    })
    void apply_whenTheOperationCannotApply_refuses(String patch) {
        // When / Then
        assertThatThrownBy(() -> applied(patch)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void apply_whenAnOperationFails_leavesTheDocumentUntouched() {
        // Given
        JsonNode document = DraftInputsMapper.tree(DOCUMENT);
        JsonNode patch = DraftInputsMapper.tree("[{\"op\":\"replace\",\"path\":\"/a\",\"value\":9},{\"op\":\"move\"}]");

        // When / Then
        assertThatThrownBy(() -> JsonPatchApplier.apply(document, patch)).isInstanceOf(BadRequestException.class);
        assertThat(document.get("a").asInt()).isEqualTo(1);
    }
}
