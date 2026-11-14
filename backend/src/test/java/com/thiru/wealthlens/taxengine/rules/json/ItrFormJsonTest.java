package com.thiru.wealthlens.taxengine.rules.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A year's {@code forms/itr-forms.json} is read form by form, and a malformed form is refused where it lies (W-090b). */
class ItrFormJsonTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String PATH = "forms/itr-forms.json";
    private static final String PROVENANCE = "{\"sourceUrl\": \"https://example.invalid/w-090b\","
            + " \"sourceDescription\": \"Synthetic\", \"accessedOn\": \"2026-11-09\","
            + " \"verificationLevel\": \"UNVERIFIED\", \"whatToCheck\": \"nothing\"}";

    private static String form(String id, String appliesTo) {
        return "{\"id\": \"" + id + "\", \"displayName\": \"Synthetic\", \"cascadeOrder\": 1,"
                + " \"appliesTo\": [" + appliesTo + "], \"returnKinds\": [\"SYN_ORIGINAL\"],"
                + " \"furnishing\": [{\"manner\": \"SYN_ELECTRONIC\", \"provenance\": " + PROVENANCE + "}],"
                + " \"provenance\": " + PROVENANCE + "}";
    }

    private static JsonNode forms(String... forms) {
        return MAPPER.readTree("{\"forms\": [" + String.join(",", forms) + "]}");
    }

    @Test
    void forms_whenOptionalListsAreAbsent_readsThemAsEmpty() {
        // Given / When
        var forms = ItrFormJson.forms(forms(form("SYN-1", "\"INDIVIDUAL\"")), PATH);

        // Then
        var form = forms.get(new ItrFormCode("SYN-1"));
        assertThat(form.positiveConditions()).isEmpty();
        assertThat(form.bars()).isEmpty();
        assertThat(form.excludes()).isEmpty();
        assertThat(form.furnishing().getFirst().conditions()).isEmpty();
    }

    @Test
    void forms_whenAPersonTypeIsUnknown_refusesNamingTheForm() {
        assertThatThrownBy(() -> ItrFormJson.forms(forms(form("SYN-1", "\"FIRM\"")), PATH))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("forms/itr-forms.json.forms[0] (SYN-1).appliesTo contains \"FIRM\"");
    }

    @Test
    void forms_whenAFormIsDefinedTwice_refuses() {
        assertThatThrownBy(() -> ItrFormJson.forms(
                        forms(form("SYN-1", "\"INDIVIDUAL\""), form("SYN-1", "\"HUF\"")), PATH))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("forms/itr-forms.json defines form SYN-1 twice");
    }
}
