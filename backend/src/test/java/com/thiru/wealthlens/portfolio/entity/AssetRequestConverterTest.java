package com.thiru.wealthlens.portfolio.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AssetRequestConverterTest {

    private final AssetRequestConverter converter = new AssetRequestConverter();

    @Test
    void convertToEntityAttribute_whenTheRequestHeldAnUploadedOrderTime_keepsIt() {
        // Given — the upload set the cell's time (AssetRequestParser.setBrokerOrderTime)
        AssetRequest request = new AssetRequest();
        request.setEmail("owner@example.com");
        request.setOrderExecutionDateTime(LocalDateTime.of(2026, 1, 15, 10, 30));

        // When
        String stored = converter.convertToDatabaseColumn(request);
        AssetRequest read = converter.convertToEntityAttribute(stored);

        // Then — the time survives; the email is stored plain since W-428a (D-1036) and read back
        assertThat(read.getOrderExecutionDateTime()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 30));
        assertThat(stored).contains("\"email\":\"owner@example.com\"");
        assertThat(read.getEmail()).isEqualTo("owner@example.com");
    }

    @Test
    void requestBody_whenItNamesTheOrderTime_stillLeavesItUnset() {
        // Given — an API caller sending the field the stored form keeps
        String body = "{\"stockCode\": \"INFY\", \"orderExecutionDateTime\": \"2026-01-15T10:30:00\"}";

        // When
        AssetRequest request = JsonMapper.builder().build().readValue(body, AssetRequest.class);

        // Then — the request contract is unchanged
        assertThat(request.getStockCode()).isEqualTo("INFY");
        assertThat(request.getOrderExecutionDateTime()).isNull();
    }
}
