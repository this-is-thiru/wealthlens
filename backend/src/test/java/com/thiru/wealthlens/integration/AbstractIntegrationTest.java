package com.thiru.wealthlens.integration;

import com.thiru.wealthlens.auth.config.AccessTokenConfig;
import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeAccountRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeInstrumentRepository;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.corporate.repository.CorporateActionRepository;
import com.thiru.wealthlens.corporate.repository.LastlyPerformedCorporateActionRepo;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.testsupport.FakeClamd;
import io.restassured.RestAssured;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Isolated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

    /**
     * Reference data shared read-only by every test. Everything else is per-test state and is cleared after each test. Anything not listed here is wiped, so a new table needs no
     * change to this class — which is the point.
     */
    private static final Set<String> SEEDED_REFERENCE_TABLES = Set.of(
            // Written by ChargeSeederService, which since ADR-27 runs only when asked. Dropping it
            // between tests would leave whichever test seeds it carrying the cost for the rest, and
            // any rate card published afterwards would be rejected for naming codes the catalogue
            // no longer holds. It is reference data; it survives.
            //
            // charge_schedules is deliberately NOT here. Tests assert over the cards they create,
            // and shipped cards in the same table would make those assertions depend on which
            // class happened to run first. A test needing a rate card writes one.
            "charge_catalogue");

    static final PostgreSQLContainer postgresContainer;
    static {
        /*
         * max_connections raised from Postgres's default 100 (W-337e): Spring's test context cache holds up to 32
         * contexts at once (spring.test.context.cache.maxSize), one HikariCP pool each at Boot's default size 10 — up
         * to 320 possible connections across the whole run's distinct property combinations, all against this one
         * shared container. The default ceiling made a long local or CI run fail with "sorry, too many clients
         * already" partway through, unrelated to any one test's correctness.
         */
        postgresContainer = new PostgreSQLContainer("postgres:18").withCommand("postgres", "-c", "max_connections=400")
                .withInitScript("postgres-schema.sql");
        postgresContainer.start();
    }

    /** The malware scanner every upload passes through (W-131e); shared and left running, as the database containers are. */
    protected static final FakeClamd CLAMD = FakeClamd.start();

    @AfterAll
    static void tearDownContainer() {
        // Containers are shared across all test classes in the same JVM.
        // Do NOT stop here — it would kill the databases for subsequent test classes,
        // causing them to hang indefinitely. The JVM exit cleans them up.
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("app.postgres.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("app.postgres.datasource.username", postgresContainer::getUsername);
        registry.add("app.postgres.datasource.password", postgresContainer::getPassword);
        registry.add("app.tax.documents.scan.host", () -> "localhost");
        registry.add("app.tax.documents.scan.port", CLAMD::port);
    }

    @LocalServerPort
    private int port;

    @Autowired
    @Qualifier("postgresDataSource")
    protected DataSource postgresDataSource;

    @Autowired
    protected UserDetailsRepository userDetailsRepository;

    @Autowired
    protected CorporateActionRepository corporateActionRepository;

    @Autowired
    protected LastlyPerformedCorporateActionRepo lastlyPerformedCorporateActionRepo;

    @Autowired
    protected ChargeCatalogueRepository chargeCatalogueRepository;

    @Autowired
    protected ChargeScheduleRepository chargeScheduleRepository;

    @Autowired
    protected ChargeInstrumentRepository chargeInstrumentRepository;

    @Autowired
    protected ChargeAccountRepository chargeAccountRepository;

    @Autowired
    protected UserChargeRepository userChargeRepository;

    @Autowired
    protected PortfolioRepository portfolioRepository;

    @Autowired
    protected TransactionRepository transactionRepository;

    @Autowired
    protected TradeOutcomeRepository tradeOutcomeRepository;

    @Autowired
    protected ProfitAndLossRepository profitAndLossRepository;

    @Autowired
    private FieldCipher fieldCipher;

    /** The application's own encoder and clock (W-358b), so a test token is signed and timed as a real one is. */
    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private Clock clock;

    @BeforeAll
    void setUpRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    /**
     * Clears every PostgreSQL table except the seeded reference data and Flyway history. {@code TRUNCATE}, never
     * {@code DROP}: a dropped table would lose its indexes and constraints for every later class in the shared container
     * (W-300's lesson, from when collections were dropped).
     */
    @AfterEach
    void cleanDatabase() {
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'",
                String.class);
        List<String> toTruncate = tables.stream()
                .filter(table -> !table.equals("flyway_schema_history") && !SEEDED_REFERENCE_TABLES.contains(table))
                .map(table -> "\"" + table + "\"")
                .toList();
        if (!toTruncate.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", toTruncate) + " CASCADE");
        }
    }

    /** An identifier as stored (W-253): what a raw document or column holds and a raw query matches. */
    protected String storedIdentifier(String identifier) {
        return fieldCipher.encryptSearchable(identifier);
    }

    /** The login of {@code email} from PostgreSQL (W-337c1); {@code null} when there is none. */
    protected UserDetail storedLogin(String email) {
        return userDetailsRepository.findByEmail(email).orElse(null);
    }

    /** Saves a {@link UserDetail} into PostgreSQL (W-337c1). */
    protected UserDetail saveLogin(UserDetail user) {
        return userDetailsRepository.save(user);
    }

    protected String generateToken(String email) {
        return generateToken(email, "USER");
    }

    protected String generateToken(String email, String role) {
        Instant now = clock.instant();
        return token(email, role, now, now.plus(Duration.ofMinutes(15)));
    }

    protected String generateExpiredToken(String email, String role) {
        Instant issued = clock.instant().minus(Duration.ofHours(1));
        return token(email, role, issued, issued.plus(Duration.ofMinutes(15)));
    }

    private String token(String email, String role, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(email).issuedAt(issuedAt).expiresAt(expiresAt)
                .claim(AccessTokenConfig.AUTHORITIES_CLAIM, List.of("ROLE_" + role)).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
