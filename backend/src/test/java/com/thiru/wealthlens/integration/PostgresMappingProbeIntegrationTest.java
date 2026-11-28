package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.auth.entity.RefreshToken;
import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.repository.RefreshTokenRepository;
import com.thiru.wealthlens.insurance.entity.InsuranceEntity;
import com.thiru.wealthlens.insurance.entity.model.PolicyDetails;
import com.thiru.wealthlens.insurance.repository.InsuranceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the PostgreSQL 18 JPA mapping (W-337a D-937; re-pointed to the live entities in W-337c1 D-941): the converted
 * columns, an embedded list, an atomic conditional update (refresh-token spend), expiry swept with no TTL
 * index, transaction rollback, money as {@code double} and exact times, and — since W-428a (D-1036) — values stored plain
 * while the {@code k1:} ciphertext stored before it is still read.
 */
class PostgresMappingProbeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private InsuranceRepository insuranceRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void save_whenAUserIsStored_holdsItsEmailPlainAndFindsItByDerivedQuery() {
        // Given
        String email = "probe.one@example.com";
        UserDetail user = new UserDetail();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setPassword("a-hash");

        // When
        userDetailsRepository.save(user);
        String raw = new JdbcTemplate(postgresDataSource).queryForObject("SELECT email FROM user_details WHERE id = ?",
                String.class, user.getId());

        // Then — stored plain since W-428a (D-1036)
        assertThat(raw).isEqualTo(email);
        assertThat(userDetailsRepository.findByEmail(email)).isPresent().get()
                .extracting(UserDetail::getEmail).isEqualTo(email);
    }

    @Test
    void readByJpa_whenStoredInTheOldCiphertext_readsItPlain() {
        // Given — a value sealed as every row was before W-428a (D-1036)
        String email = "probe.two@example.com";
        UserDetail user = new UserDetail();
        user.setId(UUID.randomUUID().toString());
        user.setEmail("placeholder@example.com");
        userDetailsRepository.save(user);
        new JdbcTemplate(postgresDataSource).update("UPDATE user_details SET email = ? WHERE id = ?",
                oldCiphertext(email), user.getId());

        // When / Then — the JPA converter reads it back plain (a lookup by email finds it only after the rewrite, PlainTextRewriteIntegrationTest)
        assertThat(userDetailsRepository.findById(user.getId())).isPresent().get().extracting(UserDetail::getEmail)
                .isEqualTo(email);
    }

    @Test
    void spend_whenCalledTwiceConcurrently_succeedsExactlyOnce() {
        // Given
        RefreshToken token = refreshToken("tok-concurrent");
        refreshTokenRepository.save(token);
        Instant now = Instant.now();
        AtomicInteger totalRowsChanged = new AtomicInteger();

        // When — two threads race the same compare-and-set update, each its own transaction
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CompletableFuture<Integer> first = CompletableFuture
                .supplyAsync(() -> transactionTemplate.execute(status -> refreshTokenRepository.spend(token.getTokenHash(), now)));
        CompletableFuture<Integer> second = CompletableFuture
                .supplyAsync(() -> transactionTemplate.execute(status -> refreshTokenRepository.spend(token.getTokenHash(), now)));
        totalRowsChanged.addAndGet(first.join());
        totalRowsChanged.addAndGet(second.join());

        // Then — exactly one of the two changed a row, never both, never neither
        assertThat(totalRowsChanged.get()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(token.getTokenHash())).isPresent().get()
                .extracting(RefreshToken::getUsedAt).isNotNull();
    }

    @Test
    void deleteExpired_whenPastItsExpiry_isRemovedWithNoTtlIndex() {
        // Given
        RefreshToken expired = refreshToken("tok-expired");
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        refreshTokenRepository.save(expired);
        RefreshToken live = refreshToken("tok-live");
        refreshTokenRepository.save(live);

        // When
        int removed = new TransactionTemplate(transactionManager)
                .execute(status -> refreshTokenRepository.deleteExpired(Instant.now()));

        // Then
        assertThat(removed).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(expired.getTokenHash())).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(live.getTokenHash())).isPresent();
    }

    @Test
    void save_whenAnInsuranceHasEmbeddedPolicyDetails_roundTripsMoneyDatesAndTextExactly() {
        // Given
        PolicyDetails policy = new PolicyDetails();
        policy.setPolicyId("POL-1");
        policy.setPremium(0.1 + 0.2);
        policy.setStartDate(LocalDate.of(2026, 4, 1));
        policy.setEndDate(LocalDate.of(2027, 3, 31));
        policy.setAgentName("Agent Name");

        InsuranceEntity insurance = new InsuranceEntity();
        insurance.setId(UUID.randomUUID().toString());
        insurance.setEmail("probe.three@example.com");
        insurance.setPremium(0.3 + 0.3);
        insurance.setPolicyDetails(List.of(policy));

        // When
        insuranceRepository.save(insurance);
        InsuranceEntity read = insuranceRepository.findByEmail("probe.three@example.com").getFirst();

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
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UserDetail user = new UserDetail();
        user.setId(UUID.randomUUID().toString());
        user.setEmail("probe.rollback@example.com");

        // When
        try {
            transactionTemplate.executeWithoutResult(status -> {
                userDetailsRepository.save(user);
                userDetailsRepository.flush();
                throw new IllegalStateException("force a rollback");
            });
        } catch (IllegalStateException expected) {
            // the point of the test
        }

        // Then
        assertThat(userDetailsRepository.findByEmail("probe.rollback@example.com")).isEmpty();
    }

    private static RefreshToken refreshToken(String rawToken) {
        RefreshToken token = new RefreshToken();
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
