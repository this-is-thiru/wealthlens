package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.auth.entity.RefreshTokenJpa;
import com.thiru.wealthlens.auth.entity.UserDetailJpa;
import com.thiru.wealthlens.auth.repository.RefreshTokenJpaRepository;
import com.thiru.wealthlens.auth.repository.UserDetailJpaRepository;
import com.thiru.wealthlens.insurance.entity.InsuranceJpa;
import com.thiru.wealthlens.insurance.entity.model.PolicyDetailsJpa;
import com.thiru.wealthlens.insurance.repository.InsuranceJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves W-337a's mapping (D-937): the encrypted and searchable columns, an embedded list, an atomic conditional update
 * (the refresh-token spend), an expiry swept with no TTL index, a transaction, money as {@code double} and times exact,
 * and a ciphertext written by {@code SearchableIdentifier}/{@code EncryptedField} (Mongo) read by {@code SearchableColumn}/
 * {@code EncryptedColumn} (JPA) — the copier's premise (D-928 C3a). Against {@link AbstractPostgresIntegrationTest}'s
 * Postgres 18 container, not against the live Mongo-backed entities, which this probe leaves untouched.
 */
class PostgresMappingProbeIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserDetailJpaRepository userDetailJpaRepository;
    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;
    @Autowired
    private InsuranceJpaRepository insuranceJpaRepository;
    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;
    @Autowired
    @Qualifier("postgresTransactionManager")
    private PlatformTransactionManager postgresTransactionManager;

    @Test
    void save_whenAUserIsStored_holdsItsEmailEncryptedAndFindsItByDerivedQuery() {
        // Given
        String email = "probe.one@example.com";
        UserDetailJpa user = new UserDetailJpa();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setPassword("a-hash");

        // When
        userDetailJpaRepository.save(user);
        String raw = new JdbcTemplate(postgresDataSource).queryForObject("SELECT email FROM user_details WHERE id = ?",
                String.class, user.getId());

        // Then — stored encrypted, exactly as Mongo's SearchableIdentifier would store it (the copier's premise)
        assertThat(raw).startsWith("k1:").doesNotContain("probe.one").isEqualTo(storedIdentifier(email));
        assertThat(userDetailJpaRepository.findByEmail(email)).isPresent().get()
                .extracting(UserDetailJpa::getEmail).isEqualTo(email);
    }

    @Test
    void readByJpa_whenWrittenByMongosConverter_decryptsTheSameCiphertext() {
        // Given — a value sealed exactly as Mongo's SearchableIdentifier/FieldCipher would seal it
        String email = "probe.two@example.com";
        UserDetailJpa user = new UserDetailJpa();
        user.setId(UUID.randomUUID().toString());
        user.setEmail("placeholder@example.com");
        userDetailJpaRepository.save(user);
        new JdbcTemplate(postgresDataSource).update("UPDATE user_details SET email = ? WHERE id = ?",
                storedIdentifier(email), user.getId());

        // When / Then — the JPA converter reads it back unchanged
        assertThat(userDetailJpaRepository.findByEmail(email)).isPresent().get().extracting(UserDetailJpa::getEmail)
                .isEqualTo(email);
    }

    @Test
    void spend_whenCalledTwiceConcurrently_succeedsExactlyOnce() {
        // Given
        RefreshTokenJpa token = refreshToken("tok-concurrent");
        refreshTokenJpaRepository.save(token);
        Instant now = Instant.now();
        AtomicInteger totalRowsChanged = new AtomicInteger();

        // When — two threads race the same compare-and-set update, each its own transaction (a @Modifying
        // query runs only inside one -- Spring Data does not open one for a custom query method by itself)
        TransactionTemplate transactionTemplate = new TransactionTemplate(postgresTransactionManager);
        CompletableFuture<Integer> first = CompletableFuture
                .supplyAsync(() -> transactionTemplate.execute(status -> refreshTokenJpaRepository.spend(token.getTokenHash(), now)));
        CompletableFuture<Integer> second = CompletableFuture
                .supplyAsync(() -> transactionTemplate.execute(status -> refreshTokenJpaRepository.spend(token.getTokenHash(), now)));
        totalRowsChanged.addAndGet(first.join());
        totalRowsChanged.addAndGet(second.join());

        // Then — exactly one of the two changed a row, never both, never neither
        assertThat(totalRowsChanged.get()).isEqualTo(1);
        assertThat(refreshTokenJpaRepository.findByTokenHash(token.getTokenHash())).isPresent().get()
                .extracting(RefreshTokenJpa::getUsedAt).isNotNull();
    }

    @Test
    void deleteExpired_whenPastItsExpiry_isRemovedWithNoTtlIndex() {
        // Given
        RefreshTokenJpa expired = refreshToken("tok-expired");
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        refreshTokenJpaRepository.save(expired);
        RefreshTokenJpa live = refreshToken("tok-live");
        refreshTokenJpaRepository.save(live);

        // When
        int removed = new TransactionTemplate(postgresTransactionManager)
                .execute(status -> refreshTokenJpaRepository.deleteExpired(Instant.now()));

        // Then
        assertThat(removed).isEqualTo(1);
        assertThat(refreshTokenJpaRepository.findByTokenHash(expired.getTokenHash())).isEmpty();
        assertThat(refreshTokenJpaRepository.findByTokenHash(live.getTokenHash())).isPresent();
    }

    @Test
    void save_whenAnInsuranceHasEmbeddedPolicyDetails_roundTripsMoneyDatesAndEncryptedTextExactly() {
        // Given
        PolicyDetailsJpa policy = new PolicyDetailsJpa();
        policy.setPolicyId("POL-1");
        policy.setPremium(0.1 + 0.2);
        policy.setStartDate(LocalDate.of(2026, 4, 1));
        policy.setEndDate(LocalDate.of(2027, 3, 31));
        policy.setAgentName("Agent Name");

        InsuranceJpa insurance = new InsuranceJpa();
        insurance.setId(UUID.randomUUID().toString());
        insurance.setEmail("probe.three@example.com");
        insurance.setPremium(0.3 + 0.3);
        insurance.setPolicyDetails(List.of(policy));

        // When
        insuranceJpaRepository.save(insurance);
        InsuranceJpa read = insuranceJpaRepository.findByEmail("probe.three@example.com").getFirst();

        // Then
        assertThat(read.getPremium()).isEqualTo(0.6);
        assertThat(read.getPolicyDetails()).singleElement().satisfies(p -> {
            assertThat(p.getPremium()).isEqualTo(0.1 + 0.2);
            assertThat(p.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(p.getEndDate()).isEqualTo(LocalDate.of(2027, 3, 31));
            assertThat(p.getAgentName()).isEqualTo("Agent Name");
            assertThat(p.getPolicyId()).isEqualTo("POL-1");
        });
    }

    @Test
    void transaction_whenItRollsBack_leavesNothingStored() {
        // Given
        TransactionTemplate transactionTemplate = new TransactionTemplate(postgresTransactionManager);
        UserDetailJpa user = new UserDetailJpa();
        user.setId(UUID.randomUUID().toString());
        user.setEmail("probe.rollback@example.com");

        // When
        try {
            transactionTemplate.executeWithoutResult(status -> {
                userDetailJpaRepository.save(user);
                userDetailJpaRepository.flush();
                throw new IllegalStateException("force a rollback");
            });
        } catch (IllegalStateException expected) {
            // the point of the test
        }

        // Then
        assertThat(userDetailJpaRepository.findByEmail("probe.rollback@example.com")).isEmpty();
    }

    private static RefreshTokenJpa refreshToken(String rawToken) {
        RefreshTokenJpa token = new RefreshTokenJpa();
        token.setId(UUID.randomUUID().toString());
        token.setTokenHash("hash-" + rawToken);
        token.setFamilyId(UUID.randomUUID().toString());
        token.setEmail("probe.token@example.com");
        token.setFactors(List.of("FACTOR_PASSWORD"));
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
        return token;
    }
}
