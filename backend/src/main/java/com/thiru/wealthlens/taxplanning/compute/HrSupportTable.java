package com.thiru.wealthlens.taxplanning.compute;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * How likely an employer is to agree to a change of each pay component or employer fund, by its name (A2.6; W-238f).
 * Product data with no legal source, so it lives in {@code taxplanning}, never in the pack (W-238b C4a). Read once at
 * start and refused there if it is not well-formed; which names it must cover is checked against the pack at build
 * (W-238f C3a), since {@code taxplanning} cannot read the pack.
 *
 * @param likelihoods each name's value
 */
public record HrSupportTable(Map<String, HrSupportLikelihood> likelihoods) {

    /** The table the application runs with. */
    public static final String RESOURCE = "data/tax-planning/hr-support.json";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public HrSupportTable {
        if (likelihoods.isEmpty()) {
            throw new IllegalStateException("The HR-support table gives no values");
        }
        likelihoods.forEach((name, likelihood) -> {
            if (likelihood == null) {
                throw new IllegalStateException("The HR-support table gives no value for " + name);
            }
        });
        likelihoods = Map.copyOf(likelihoods);
    }

    /** The table at {@link #RESOURCE}. */
    public static HrSupportTable fromClasspath() {
        try (InputStream json = new ClassPathResource(RESOURCE).getInputStream()) {
            return read(json);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    /**
     * A table from its JSON: an object with a {@code _note} and a {@code likelihoods} object of name to value.
     *
     * @throws IllegalStateException if the JSON has a field it does not know, a name twice, an unknown value or no values
     */
    static HrSupportTable read(InputStream json) {
        try {
            return new HrSupportTable(MAPPER.readValue(json, Contents.class).likelihoods());
        } catch (JacksonException e) {
            throw new IllegalStateException("The HR-support table is not well-formed: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * The value given for a component's code or a fund's {@code EmployerFund} name.
     *
     * @throws IllegalStateException if the table gives none: only pay and employer funds move (W-238g), and
     *     {@code HrSupportCoverageTest} holds the table to both, so a missing name is a defect, never a gap to show
     */
    public HrSupportLikelihood likelihoodOf(String name) {
        HrSupportLikelihood likelihood = likelihoods.get(name);
        if (likelihood == null) {
            throw new IllegalStateException("The HR-support table gives no value for " + name);
        }
        return likelihood;
    }

    private record Contents(@JsonProperty("_note") String note, Map<String, HrSupportLikelihood> likelihoods) {

        private Contents {
            likelihoods = likelihoods == null ? Map.of() : new HashMap<>(likelihoods);
        }
    }
}
