package com.thiru.wealthlens.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

    private static final int JWT_EXPIRATION_SECONDS = 60 * 30;

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

    @AfterEach
    void cleanDatabase() {
        mongoTemplate.getDb().getCollection("transactions").drop();
        mongoTemplate.getDb().getCollection("assets").drop();
        mongoTemplate.getDb().getCollection("corporate_action").drop();
        mongoTemplate.getDb().getCollection("lastly_performed_corporate_action").drop();
        mongoTemplate.getDb().getCollection("profit_and_loss").drop();
        mongoTemplate.getDb().getCollection("reports").drop();
        mongoTemplate.getDb().getCollection("user_details").drop();
        mongoTemplate.getDb().getCollection("insurances").drop();
        mongoTemplate.getDb().getCollection("broker_charges").drop();
        mongoTemplate.getDb().getCollection("user_broker_charges").drop();

        mongoTemplate.getDb().getCollection("salary_profiles").drop();
        mongoTemplate.getDb().getCollection("tax_computations").drop();
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
