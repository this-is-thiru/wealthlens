package com.thiru.wealthlens.taxengine.golden;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One {@code DR 08} case as a file: the facts it states and the figure it expects, copied, never
 * computed (D-083).
 *
 * @param ageAtYearEnd the completed age on the tax year's last day, where the case states one
 * @param expectedPayable the case's "Expected" — rounded, so compared with {@code round.netpayable}
 */
record GoldenCase(
        String id,
        String source,
        RegimeCode regime,
        Money totalIncome,
        Optional<Integer> ageAtYearEnd,
        List<TaxYearId> taxYears,
        Money expectedPayable) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static GoldenCase load(String resource) {
        try (InputStream in = GoldenCase.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return of(MAPPER.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " could not be read", e);
        }
    }

    static List<GoldenCase> loadAll(String directory) {
        URL url = GoldenCase.class.getClassLoader().getResource(directory);
        if (url == null) {
            throw new IllegalStateException(directory + " is not on the classpath");
        }
        try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
            List<GoldenCase> cases = new ArrayList<>();
            files.filter(file -> file.toString().endsWith(".json")).sorted()
                    .forEach(file -> cases.add(load(directory + "/" + file.getFileName())));
            if (cases.isEmpty()) {
                throw new IllegalStateException(directory + " holds no cases");
            }
            return cases;
        } catch (IOException e) {
            throw new UncheckedIOException(directory + " could not be listed", e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(directory + " has no file-system path", e);
        }
    }

    private static GoldenCase of(JsonNode node) {
        List<TaxYearId> years = new ArrayList<>();
        node.get("taxYears").forEach(year -> years.add(taxYear(year.asString())));
        return new GoldenCase(
                node.get("id").asString(),
                node.get("source").asString(),
                new RegimeCode(node.get("regime").asString()),
                Money.of(node.get("totalIncome").asString()),
                node.has("ageAtYearEnd") ? Optional.of(node.get("ageAtYearEnd").asInt()) : Optional.empty(),
                List.copyOf(years),
                Money.of(node.get("expected").get("payable").asString()));
    }

    private static TaxYearId taxYear(String label) {
        TaxYearId year = TaxYearId.of(Integer.parseInt(label.substring(0, 4)));
        if (!year.label().equals(label)) {
            throw new IllegalArgumentException("\"" + label + "\" is not a tax year label like 2026-27");
        }
        return year;
    }

    @Override
    public String toString() {
        return id;
    }
}
