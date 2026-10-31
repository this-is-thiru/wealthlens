package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The instruments a lock names (D-210): the year's registry entry, read where the running application reads
 * it. Expected values are typed from each lock's {@code instruments} block.
 */
class RulePackLockTest {

    private static final Path LOCK_2026 = Path.of("src/main/resources/rulepacks/2026-27.lock.json");
    private static final Path LOCK_2025 = Path.of("src/main/resources/rulepacks/2025-26.lock.json");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void read_whenTheLockIs2026_readsTheActTheFinanceActAndTheRules() throws IOException {
        // Given
        String content = Files.readString(LOCK_2026);

        // When
        GoverningInstruments instruments = RulePackLock.read(TaxYearId.of(2026), content, "2026-27").instruments();

        // Then
        assertThat(instruments.act().citation()).isEqualTo("Income-tax Act, 2025 (30 of 2025)");
        assertThat(instruments.act().assentedOn()).contains(LocalDate.of(2025, 8, 21));
        assertThat(instruments.act().inForceFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(instruments.act().gazetteRef()).isEqualTo("CG-DL-E-22082025-265620");
        assertThat(instruments.act().sourceUrl())
                .isEqualTo(URI.create("https://egazette.gov.in/WriteReadData/2025/265620.pdf"));
        assertThat(instruments.act().verificationLevel()).isEqualTo(VerificationLevel.SOURCE_CHECKED);
        assertThat(instruments.financeAct().citation()).isEqualTo("Finance Act, 2026 (4 of 2026)");
        assertThat(instruments.financeAct().gazetteRef()).isEqualTo("CG-DL-E-31032026-271439");
        assertThat(instruments.rules().citation()).isEqualTo("Income-tax Rules, 2026 (G.S.R. 198(E), 20-03-2026)");
        assertThat(instruments.rules().assentedOn()).isEqualTo(Optional.empty());
        assertThat(instruments.rules().gazetteRef()).isEqualTo("CG-DL-E-20032026-271092");
    }

    @Test
    void read_whenTheLockIs2025_readsThe1961ActAndThe1962Rules() throws IOException {
        // Given
        String content = Files.readString(LOCK_2025);

        // When
        GoverningInstruments instruments = RulePackLock.read(TaxYearId.of(2025), content, "2025-26").instruments();

        // Then
        assertThat(instruments.act().citation()).isEqualTo("Income-tax Act, 1961 (43 of 1961)");
        assertThat(instruments.act().assentedOn()).contains(LocalDate.of(1961, 9, 13));
        assertThat(instruments.financeAct().citation()).isEqualTo("Finance Act, 2026 (4 of 2026)");
        assertThat(instruments.rules().citation()).isEqualTo("Income-tax Rules, 1962");
        assertThat(instruments.rules().inForceFrom()).isEqualTo(LocalDate.of(1962, 4, 1));
    }

    @Test
    void read_whenTheLockNamesNoInstruments_refusesNamingTheLock() throws IOException {
        // Given
        ObjectNode lock = (ObjectNode) MAPPER.readTree(Files.readString(LOCK_2026));
        lock.remove("instruments");

        // When / Then
        assertThatThrownBy(() -> RulePackLock.read(TaxYearId.of(2026), lock.toString(), "2026-27.lock.json"))
                .isInstanceOf(PackValidationException.class)
                .hasMessage("2026-27.lock.json.instruments is required and is absent");
    }

    @Test
    void read_whenAnInstrumentHasAnUnknownVerificationLevel_refusesNamingTheInstrument() throws IOException {
        // Given
        ObjectNode lock = (ObjectNode) MAPPER.readTree(Files.readString(LOCK_2026));
        ((ObjectNode) lock.get("instruments").get("rules")).put("verificationLevel", "GUESSED");

        // When / Then
        assertThatThrownBy(() -> RulePackLock.read(TaxYearId.of(2026), lock.toString(), "2026-27.lock.json"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageStartingWith("2026-27.lock.json.instruments.rules.verificationLevel must be one of");
    }
}
