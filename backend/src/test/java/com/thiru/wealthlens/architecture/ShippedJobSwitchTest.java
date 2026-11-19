package com.thiru.wealthlens.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Every shipped profile keeps the tax jobs off (W-336, D-783): the app runs only on the owner's machine, so a job runs when the
 * owner asks through {@code TaxJobController}, never on a clock that fires only if the laptop happens to be up.
 */
class ShippedJobSwitchTest {

    @ParameterizedTest
    @ValueSource(strings = {"application.yaml", "application-staging.yaml", "application-prod.yaml"})
    void jobsEnabled_whenAProfileShips_isFalse(String profile) {
        // Given
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(profile));

        // When
        Properties properties = yaml.getObject();

        // Then
        assertThat(properties).isNotNull();
        assertThat(properties.get("app.tax.jobs.enabled")).hasToString("false");
    }
}
