package com.thiru.wealthlens.integration;

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
import org.testcontainers.containers.MongoDBContainer;

@Tag("integration")
@Isolated
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

    private static final int JWT_EXPIRATION_SECONDS = 60 * 30;

    /**
     * Collections seeded once per Spring context (PolicySeederService runs at @PostConstruct)
     * and shared read-only by every test. Everything else is per-test state and is cleared
     * after each test. Anything not listed here is wiped, so a new collection needs no
     * change to this class — which is the point.
     */
    private static final Set<String> SEEDED_REFERENCE_COLLECTIONS = Set.of(
            "allowance_catalogue",
            "allowance_limits",
            "tax_slab_policies",
            "tax_year_registry",
            "perquisite_policies");

    static final MongoDBContainer mongoDBContainer;
    static {
        mongoDBContainer = new MongoDBContainer("mongo:7.0");
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

    @BeforeAll
    void setUpRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    /**
     * Clears every collection except the seeded reference data.
     *
     * <p>Uses {@code deleteMany} rather than {@code drop} deliberately: dropping a collection
     * also drops its indexes, forcing a rebuild on the next write. Deleting documents is faster
     * and keeps index behaviour under test realistic.
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
