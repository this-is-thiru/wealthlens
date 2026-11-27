package com.thiru.wealthlens.corporate.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.entity.model.DemergerDetail;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CorporateActionResponseTest {

    @Test
    void from_whenADemergerIsAnswered_carriesItsDetailWithoutAnEmptyFlag() {
        // Given — m1.6.1 answered a demerger's detail as its fields alone (W-419, D-1002)
        DemergerDetail detail = new DemergerDetail();
        detail.setDemergerRatio("1:1");
        detail.setMainStockCode("RELIANCE");
        CorporateActionEntity action = new CorporateActionEntity();
        action.setStockCode("RELIANCE");
        action.setType(CorporateActionType.DEMERGER);
        action.setDemergerDetail(detail);

        // When
        JsonNode answered = JsonMapper.builder().build().valueToTree(CorporateActionResponse.from(action)).get("demergerDetail");

        // Then
        assertThat(answered.get("mainStockCode").asString()).isEqualTo("RELIANCE");
        assertThat(answered.has("empty")).isFalse();
    }
}
