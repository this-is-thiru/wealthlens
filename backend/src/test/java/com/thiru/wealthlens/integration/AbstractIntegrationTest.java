package com.thiru.wealthlens.integration;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.taxplanning.policy.service.PolicySeederService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;

@Tag("integration")
@Isolated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

    private static final int JWT_EXPIRATION_SECONDS = 60 * 30;

    /**
     * Collections the tax-policy seed fills ({@link #seedTaxPolicies()}, before every test — idempotent, so after
     * the first it writes nothing) and shared read-only by every test. Everything else is per-test state and is cleared
     * after each test. Anything not listed here is wiped, so a new collection needs no
     * change to this class — which is the point.
     */
    private static final Set<String> SEEDED_REFERENCE_COLLECTIONS = Set.of(
            "allowance_catalogue",
            "allowance_limits",
            "tax_slab_policies",
            "tax_year_registry",
            "perquisite_policies",
            // Written by ChargeSeederService, which since ADR-27 runs only when asked. Dropping it
            // between tests would leave whichever test seeds it carrying the cost for the rest, and
            // any rate card published afterwards would be rejected for naming codes the catalogue
            // no longer holds. It is reference data; it survives.
            //
            // charge_schedules is deliberately NOT here. Tests assert over the cards they create,
            // and shipped cards in the same collection would make those assertions depend on which
            // class happened to run first. A test needing a rate card writes one.
            "charge_catalogue");

    /**
     * A single-node replica set, because this application's multi-document writes need one.
     *
     * <p><b>{@code withReplicaSet()} is not optional.</b> The superseded
     * {@code org.testcontainers.containers.MongoDBContainer} always initialised a replica set;
     * {@code org.testcontainers.mongodb.MongoDBContainer} does so only when asked. Dropping it
     * would leave {@code app.mongodb.transactions-enabled=true} pointed at a standalone server, and
     * every {@code @Transactional} write spanning two collections would fail.
     *
     * <p><b>Pinned to 8.0.4.</b> Later 8.0 patches refuse to start on Linux 6.19 and newer
     * (SERVER-121912), which CI's Ubuntu 26 runner has (kernel 7.0).
     */
    static final MongoDBContainer mongoDBContainer;
    static {
        mongoDBContainer = new MongoDBContainer("mongo:8.0.4").withReplicaSet();
        mongoDBContainer.start();
    }

    @AfterAll
    static void tearDownContainer() {
        // Container is shared across all test classes in the same JVM.
        // Do NOT stop here — it would kill MongoDB for subsequent test classes,
        // causing them to hang indefinitely. The JVM exit cleans it up.
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("app.mongodb.transactions-enabled", () -> "true");
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    private PolicySeederService policySeederService;

    @Autowired
    private FieldCipher fieldCipher;

    @BeforeAll
    void setUpRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    /** The policies the application no longer seeds at boot (D-280): tests that compute tax need them on file. */
    @BeforeEach
    void seedTaxPolicies() {
        policySeederService.seed(false, "integration-test");
    }

    /**
     * Clears every collection except the seeded reference data.
     *
     * <p>Uses {@code deleteMany} rather than {@code drop} deliberately: dropping a collection
     * also drops its indexes, and nothing recreates them until the next startup
     * ({@code PortfolioIndexInitializer}), so every later class in the shared container would run
     * without its unique indexes (W-300). {@code ArchitectureTest.nothing_drops_a_collection} holds it.
     */
    @AfterEach
    void cleanDatabase() {
        for (String collectionName : mongoTemplate.getDb().listCollectionNames()) {
            if (SEEDED_REFERENCE_COLLECTIONS.contains(collectionName)) {
                continue;
            }
            mongoTemplate.getDb().getCollection(collectionName).deleteMany(new Document());
        }
    }

    /** An identifier as stored (W-253): what a raw document holds and a raw query matches. */
    protected String storedIdentifier(String identifier) {
        return fieldCipher.encryptSearchable(identifier);
    }

    /** The login of {@code email}, which is a field now rather than the id (W-253 C2a); {@code null} when there is none. */
    protected UserDetail storedLogin(String email) {
        return mongoTemplate.findOne(query(where("email").is(email)), UserDetail.class);
    }

    protected String generateToken(String email) {
        return generateToken(email, "USER");
    }

    protected String generateToken(String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", java.util.List.of("ROLE_" + role));

        Date now = new Date();
        Date expiration = new Date(now.getTime() + 1000L * JWT_EXPIRATION_SECONDS);

        String base64Key = "dGVzdC1zZWNyZXQta2V5LWZvci1pbnRlZ3JhdGlvbi10ZXN0cy1vbmx5";
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        SecretKey secretKey = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    protected String generateExpiredToken(String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", java.util.List.of("ROLE_" + role));

        Date now = new Date(System.currentTimeMillis() - 1000L * 60 * 60);
        Date expiration = new Date(now.getTime() - 1000L);

        String base64Key = "dGVzdC1zZWNyZXQta2V5LWZvci1pbnRlZ3JhdGlvbi10ZXN0cy1vbmx5";
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        SecretKey secretKey = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }
}
