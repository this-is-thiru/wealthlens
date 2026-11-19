package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The engine against the independent oracle ({@code DR 19} E4.2; W-141): {@code test-oracle/oracle.py}, written from the
 * Gazette with no code or pack of the engine's, wrote {@code expected.csv}; every row must be what the engine pays. A
 * difference means one of the two is wrong, and a person decides which (C4a). The build has no Python, so the file's
 * first line records the sha256 of the oracle and its cases, and a file changed without regenerating fails here first.
 */
class OracleAgreementTest {

    private static final Path ORACLE = Path.of("../test-oracle");
    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    @Test
    void expected_whenRead_wasWrittenByTheOracleAndCasesAsTheyStandNow() throws IOException {
        // Given
        String fingerprint = Files.readAllLines(ORACLE.resolve("expected.csv")).getFirst();

        // When / Then
        assertThat(fingerprint)
                .as("expected.csv is stale: run python3 test-oracle/cases.py and python3 test-oracle/oracle.py, and read the diff")
                .isEqualTo("# oracle.py sha256 " + sha256(ORACLE.resolve("oracle.py")) + " cases.csv sha256 "
                        + sha256(ORACLE.resolve("cases.csv")));
    }

    @Test
    void run_whenEveryOracleRowIsComputed_paysWhatTheOracleDoes() throws IOException {
        // Given
        List<String> rows = Files.readAllLines(ORACLE.resolve("expected.csv"));

        // When
        List<String> differences = new ArrayList<>();
        for (String row : rows.subList(2, rows.size())) {
            String[] field = row.split(",", -1);
            TaxYearId year = GoldenCase.taxYear(field[2]);
            Money expected = Money.of(field[4]);
            GoldenCase golden = new GoldenCase("oracle " + row, "test-oracle/expected.csv", new RegimeCode(field[0]),
                    Money.of(field[3]), field[1].isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(field[1])),
                    List.of(year), expected);
            NodeValue paid = RUNNER.run(golden, year).result().valueOf(NodeIds.ROUND_NETPAYABLE);
            if (!paid.equals(MoneyValue.of(expected))) {
                differences.add(row + " — engine " + paid);
            }
        }

        // Then
        assertThat(rows.size() - 2).as("rows compared").isPositive();
        assertThat(differences).as("rows where the engine and the oracle differ: one of them is wrong").isEmpty();
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
        }
    }
}
