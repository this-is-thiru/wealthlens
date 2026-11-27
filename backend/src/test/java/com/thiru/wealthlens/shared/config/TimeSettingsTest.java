package com.thiru.wealthlens.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class TimeSettingsTest {

    private static TimeSettings bound(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties)).bindOrCreate("app.time", TimeSettings.class);
    }

    @Test
    void bind_whenAZoneIsNamed_isThatZone() {
        // When
        TimeSettings settings = bound(Map.of("app.time.zone", "Asia/Kolkata"));

        // Then
        assertThat(settings.zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void bind_whenNoZoneIsNamed_isRefused() {
        // When / Then
        assertThatThrownBy(() -> bound(Map.of())).hasRootCauseInstanceOf(NullPointerException.class)
                .rootCause().hasMessageContaining("app.time.zone is required");
    }

    @Test
    void bind_whenTheZoneIsUnknown_isRefused() {
        // When / Then
        assertThatThrownBy(() -> bound(Map.of("app.time.zone", "Asia/Nowhere"))).isInstanceOf(BindException.class);
    }
}
