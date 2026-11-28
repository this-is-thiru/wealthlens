package com.thiru.wealthlens.portfolio.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AssetRequestConverterTest {

    @Mock
    private FieldCipher cipher;

    @InjectMocks
    private AssetRequestConverter converter;

    @Test
    void convertToEntityAttribute_whenTheRequestHeldAnUploadedOrderTime_keepsIt() {
        // Given — the upload set the cell's time (AssetRequestParser.setBrokerOrderTime); the cipher reveals what it is given
        given(cipher.reveal(anyString())).willAnswer(call -> call.getArgument(0));
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
