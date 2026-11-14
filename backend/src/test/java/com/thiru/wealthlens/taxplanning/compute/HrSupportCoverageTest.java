package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The HR-support table names exactly the pack's {@code SALARY} components and the employer funds (W-238f C3a): a row or
 * fund added without a value fails the build here, because {@code taxplanning} cannot read the pack at start.
 */
class HrSupportCoverageTest {

    private static final String COMPONENTS = "classpath:rulepacks/timeline/exec/components/*.json";

    @Test
    void likelihoods_whenComparedWithThePack_nameEverySalaryComponentAndFundAndNothingElse() throws IOException {
        // Given
        Set<String> expected = new TreeSet<>(salaryComponents());
        Arrays.stream(EmployerFund.values()).map(Enum::name).forEach(expected::add);

        // When
        Set<String> given = new TreeSet<>(HrSupportTable.fromClasspath().likelihoods().keySet());

        // Then
        assertThat(given).isEqualTo(expected);
    }

    private static Set<String> salaryComponents() throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        Set<String> codes = new TreeSet<>();
        for (Resource row : new PathMatchingResourcePatternResolver().getResources(COMPONENTS)) {
            try (InputStream json = row.getInputStream()) {
                JsonNode component = mapper.readTree(json);
                if ("SALARY".equals(component.get("kind").asString())) {
                    codes.add(component.get("code").asString());
                }
            }
        }
        assertThat(codes).as("the pack's SALARY rows").isNotEmpty();
        return codes;
    }
}
