# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

WealthLens — a Spring Boot 4 + Spring Modulith portfolio tracker for the Indian stock market (transactions, corporate actions, P&L, broker/AMC charges, insurance, tax planning), backed by MongoDB. Single deployable JAR.

Java 25, Maven multi-module (`backend`, `test-report`), Lombok, Log4j2, Spring Security + JWT, Apache POI (Excel), Flying Saucer + iTextPDF (PDF), Thymeleaf.

## Commands

```bash
# Build (skip tests)
./mvnw clean install -pl backend -am -DskipTests

# Run the app (default profile → it-staging Atlas cluster; needs MONGO_USER, MONGO_PASSWORD, JWT_SECURITY_KEY)
./mvnw spring-boot:run -pl backend

# All tests (unit + integration; integration needs Docker)
./mvnw test -pl backend

# Unit tier only — no Docker, ~160 tests in ~7s. The fast inner loop.
./mvnw test -pl backend -Punit

# Integration tier only (module-level tests against Testcontainers Mongo)
./mvnw test -pl backend -Pintegration

# Mutation testing — scoped to the calculation engines only
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage -Pmutation -pl backend

# Single test class / single method
./mvnw test -pl backend -Dtest=PortfolioServiceTest
./mvnw test -pl backend -Dtest=PortfolioServiceTest#addTransaction_whenBuy_createsAsset

# Full CI equivalent: runs tests then generates the consolidated HTML report
./mvnw clean test verify
# → test-report/target/consolidated-test-report.html

# Format (spotless is configured but NOT bound to a phase — run it explicitly)
./mvnw spotless:apply
```

### Build gotchas

- **A failing test now fails the build.** `maven.test.failure.ignore` defaults to `false`; CI passes `-Dmaven.test.failure.ignore=true` on its own invocation so `verify` is still reached and the consolidated HTML report is generated. CI's surefire-XML gate (`failures="[1-9]"` / `errors="[1-9]"`, plus a minimum report count) is what fails the pipeline there.
- **Integration tests require Docker** and are tagged `integration` via `AbstractIntegrationTest` (Testcontainers `mongo:7.0` replica set). They still run by default; use `-Punit` to exclude them for a fast loop or when Docker is unavailable.
- **Tests run in parallel by class** (`backend/src/test/resources/junit-platform.properties`); methods within a class share a thread. `AbstractIntegrationTest` is `@Isolated` — every integration class runs alone, because RestAssured's `baseURI`/`port` are static and all integration classes share one Mongo container. Do not remove that annotation.
- **Coverage** is measured by JaCoCo (`backend/target/site/jacoco/`). The `check` gate is scoped to `com.thiru.wealthlens.brokercharges.engine*` at 90% line / 85% branch, so it enforces only where intended rather than failing globally.
- **Money is never asserted with a bare `assertEquals`.** Use `com.thiru.wealthlens.testsupport.MoneyAssert` — exact double comparison on rupee amounts is a latent failure.
- **`ArchitectureTest`** encodes the conventions below as ArchUnit rules (no field injection, controllers do not reach repositories, snake_case `@Field` names, no console printing). Introducing a violation fails the build.
- Checkstyle runs at `validate` but is advisory (`failOnViolation=false`); it only enforces `AvoidStarImport`.
- Third-party versions not managed by the Spring Boot parent live in the root `pom.xml` `<dependencyManagement>`; module poms declare deps without versions.

## Architecture

### Spring Modulith boundaries

Modules live under `com.thiru.wealthlens.<module>`, each with a `package-info.java` carrying `@ApplicationModule(type = Type.OPEN, allowedDependencies = {...})`. `WealthLensModulithTest.modulithStructureIsValid()` runs `ApplicationModules.verify()` — **any new cross-module import must be added to the consuming module's `allowedDependencies` or this test fails.**

| Module | Allowed dependencies |
|---|---|
| `shared` | `portfolio` |
| `auth` | `shared` |
| `portfolio` | `shared`, `auth`, `corporate`, `brokercharges`, `helper` |
| `corporate` | `shared`, `portfolio` |
| `brokercharges` | `shared`, `portfolio`, `corporate` |
| `helper` | `shared`, `portfolio` |
| `taxplanning` | `shared`, `auth` |
| `insurance`, `finance` | `shared` |

`portfolio` is the domain center; `shared` depends *back* on `portfolio` because generic tools (XirrCalculator, ExcelBuilder, MongoConfig converters) reference portfolio types. All modules are `OPEN`, so internal sub-packages are still visible — that is a migration affordance from the old flat layout, not a design goal.

Within every module: `controller/` (no business logic) → `service/` (`@Transactional`) → `repository/` (Spring Data MongoDB) → `entity/` + `dto/`.

### Cross-cutting behaviour to know before debugging responses

- `shared/advice/ResponseWrapperAdvice` wraps **every** JSON response body in `ApiResponse<T>` — except under the `integration-test` profile, where it is disabled (`@Profile("!integration-test")`). Integration tests therefore assert unwrapped payloads.
- `shared/exception/ControllerAdviser` maps `IllegalArgumentException` → 400, everything else → 500. `BadRequestException extends IllegalArgumentException`. Messages are human-readable strings, never codes.
- `auth/filter/AuthFilter` (a `OncePerRequestFilter`) validates the JWT before `UsernamePasswordAuthenticationFilter`; sessions are stateless, tokens expire in 30 min, HmacSHA256 signed with `app.security.key-secret`. Public paths are listed in `auth/config/AuthConfig` (`/auth/login`, `/auth/register`, `/helper/**`, `/finances/**`, `/template/**`, `/tax-planning/public/**`).
- MongoDB multi-document `@Transactional` writes need a replica set **and** `app.mongodb.transactions-enabled=true`.

### Domain rules that are not obvious from the code

**Transaction flow** — before processing, `PortfolioService` checks for existing temporary transactions and whether a corporate action blocks the trade. `FILTERABLE_CORPORATE_ACTIONS` (BONUS, DEMERGER, STOCK_SPLIT) block new transactions: the transaction is saved with `status = TEMPORARY` and its original `AssetRequest` is kept in the `assetRequest` field. Redrive with `POST /temporary-transactions/user/{email}/redrive`; `sourceTempTransactionId` links the processed transaction back to its original.

**Two live buy/sell flows.** `addTransaction` (`POST /transactions/user/{email}/transaction`) and `addTransactionV2` (`.../transaction/v2`) coexist in `PortfolioService`. V2 creates a separate `AssetEntity` per buy (no same-day lot merging), records broker charges into P&L for EQUITY, and sells via `findEligibleHoldingsForSell(...)` FIFO over lots with `transactionDate ≤ sellDate`. Changes to sell/cost-basis logic usually need to be made in both.

**Corporate actions** are processed per calendar quarter; `LastlyPerformedCorporateAction` records the last processed action per user/stock/asset-type/action-type/broker to prevent duplicates and drive blocking.

**Broker charges** — `BrokerCharges` templates are validity-windowed (`startDate`–`endDate`) per broker. `UserBrokerChargeService` resolves the active template for the transaction date and computes brokerage (MIN/MAX aggregator across percentage and fixed), government charges (STT + SEBI, plus stamp duty on BUY only), DP charges (first SELL per stock per day only), and GST parsed component-wise from a string like `18%-brokerage,18%-dp_charges,18%-stt`. AMC is imposed in bulk via `POST /broker-charges/amc/impose` against `AssetManagementDetails`.

**P&L** — `ProfitAndLossEntity` carries two parallel hierarchies: capital gains (`RealisedProfits → FinancialReport → MonthlyReport → FortnightReport`) and broker charges (`RealisedProfits → YearlyBrokerCharges → MonthlyBrokerCharges → BrokerChargesReport`).

**Tax planning** (`taxplanning`, ~56 classes — the README still calls it a stub, it isn't) — `PolicySeederService` seeds slab/perquisite/allowance policies from `backend/src/main/resources/data/tax-policies/*.json` at `@PostConstruct`. `TaxEngineFactory` dispatches to `OldRegimeTaxEngine` / `NewRegimeTaxEngine` (both extend `AbstractTaxEngine`); policies, allowance limits and formulas are data-driven per tax year via `FormulaEvaluator` and `AllowanceResolutionService`. Flow: salary profile → compute → restructure recommendation → PDF report.

## Conventions

- `@RequiredArgsConstructor` for DI; injected fields are `private final`. No hand-written constructors.
- Services: `@Service @Log4j2 @Transactional @RequiredArgsConstructor`. Entities: `@Data @AllArgsConstructor @NoArgsConstructor @Document @Field`. DTOs: `@Data @NoArgsConstructor @ToString`; avoid `@Builder` on request DTOs.
- MongoDB: collections and fields are `snake_case` (`@Document(value = "transactions")`, `@Field("stock_code")`), id via `@MongoId`, entities implement `AuditableEntity` for `AuditMetadata`. Prefer Spring Data derived queries (`findByEmailAndStatus`).
- REST paths lowercase-hyphenated, user-scoped under `/{resource}/user/{email}`. Dates `yyyy-MM-dd`, datetimes `yyyy-MM-dd'T'HH:mm:ss` via `@JsonFormat`. All times stored UTC via `TLocalDateTime.atUtc()`.
- Static utilities in `shared/util` are `T`-prefixed: `TCollectionUtil`, `TJsonMapper`, `TStringUtil`, `TOptional`, `TLocalDate`/`TLocalDateTime`/`TLocalTime`. Excel: `ExcelBuilder`/`ExcelParser`. Dynamic Mongo queries: `QueryBuilder`/`QueryFilter`.
- 4-space indent, K&R braces, no star imports.
- Tests: `@ExtendWith(MockitoExtension.class)` with `@Mock`/`@InjectMocks`, `// Given` `// When` `// Then` sections, methods named `methodName_whenCondition_expectedResult`. Integration tests extend `AbstractIntegrationTest` (shared Testcontainers Mongo, `generateToken(email, role)` helper, per-test collection drops in `cleanDatabase()` — add any new collection there).

## Notes

- `.kimchi/docs/` holds design/analysis documents written during past feature work (module dependency maps, tax-planning specs, migration plans). Useful background, not authoritative about current code.
- `api-collection/` is an OpenCollection-format HTTP request collection covering the API surface.
- The root `README.md` documents the data model in detail but predates the tax-planning build-out; trust the code over it.
