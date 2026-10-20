# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

WealthLens — a Spring Boot 4 + Spring Modulith portfolio tracker for the Indian stock market (transactions, corporate actions, P&L, broker/AMC charges, insurance, tax planning), backed by MongoDB. Single deployable JAR.

Java 25, Maven multi-module (`backend`, `test-report`), Lombok, Log4j2, Spring Security + JWT, Apache POI (Excel), Flying Saucer + iTextPDF (PDF), Thymeleaf.

## Engineering standards — apply to every line, in every module

These are not per-feature. They apply to any change in this repository, by anyone, and they
override convenience. Where the build enforces one, the enforcing check is named.

### 1. Truth — never state what you have not checked

- **Every name you use must already exist.** Before referencing a type, method, constant, enum value, config key, endpoint, file or library API, find it (`grep`/`find`/read the file). Before inventing one, confirm nothing equivalent exists. Enum constants are as likely to be imagined as types.
- **Every claim carries its evidence.** A code claim cites `path:line` you actually read. "Tests pass" means you ran them and can paste the output. A number, rate, date, legal section or form reference is copied from a cited source, never recalled.
- **Unknown is an answer.** If a fact cannot be verified, say so and stop; do not fill the gap with something plausible. A plausible wrong answer is the most expensive defect in this codebase.
- **Report what happened, not what was intended.** Failed, skipped and partial work is reported as such, with the output.

### 2. Scope — no slop

- **Do what was asked, and nothing else.** No drive-by refactors, no extra features, no "while I'm here". Anything else worth doing is written down as a proposal, not done.
- **No speculative code.** No interface with one implementation unless it is a decided seam; no parameter, field, flag, overload or config option without a current caller; no generality for a case that does not exist yet.
- **No dead weight.** No unused imports, methods, fields or classes; no commented-out code; no `TODO`/`FIXME` without a tracked id (e.g. `TODO(W-061)`).
- **Comments say *why*, never *what*.** Delete any comment that restates the code. Javadoc on public types states the contract and the reason the type exists.
- **Nothing silent.** No `catch (Exception|RuntimeException)`, no swallowed exception, no default returned in place of a failure, no `null` passed across a boundary to mean "absent" (use `Optional` at boundaries, empty collections inside).

### 3. Naming

- **A name states the domain concept, not the mechanism.** Classes are nouns (`SlabLadder`, `ComputationReceipt`); methods are verbs or questions (`apply`, `isResident`); booleans read as predicates (`is`/`has`/`can`/`should`).
- **Banned as class-name suffixes in new code:** `Helper`, `Util`/`Utils` (outside `shared/util`'s existing `T`-prefixed classes), `Manager`, `Impl`, `Data`, `Info`, `Processor`, `Common`, `Misc`. If the only name you can find is generic, the responsibility is not yet understood.
- **One word per concept, repo-wide.** Before naming something, search for the existing word and reuse it; never introduce a synonym. Two classes with the same simple name in different modules are a defect.
- **No abbreviations** except established domain terms (PAN, HRA, TDS, ITR, NPS, DA, LTCG). Units and scale in names where a number is ambiguous (`ratePercent`, `amountRupees`).
- **Tests:** `methodName_whenCondition_expectedResult`, with `// Given` `// When` `// Then`.
- **Commits:** `<scope>: <imperative verb> <what> — <why>`; the subject says what changed, never a slogan.

### 4. Design

- Small units: a method is at most 60 lines, a class has one reason to change, more than 7 parameters means a missing type — the same limits Checkstyle enforces (`MethodLength` 60, `ParameterNumber` 7; D-288).
- Immutable by default: records for values, `private final` fields, defensive copies of collections.
- Fail fast and loudly at the boundary; validate once, on the way in.
- Dependencies point one way; a new cross-module import needs the `allowedDependencies` change and a reason.
- Money in the tax modules is `Money`, never `double`/`float`/`long`; nowhere may money be compared with a bare `assertEquals`.
- **Everything extendable — facts, not rules.** Models and read-models publish facts (dates, events, modes, amounts),
  never a flag or label derived from today's law (`acquiredBeforeX`, `isEligible`, a term computed from a fixed
  threshold). Every legal date, boundary, rate or qualifying list is tax-year-versioned rule data on the tax side,
  never a Java constant, `if` or `switch`, so a law that changes, lapses and returns is new data, not new code. Open
  categories pass through as themselves (carry the `CorporateActionType`, don't collapse it into a catch-all).
  Existing violations are listed by `W-088l`; a new one is a defect.

### 5. Tests

- Expected values are derived **independently of the code** (from the source, by hand, shown) — never by running the implementation and copying its output.
- A test must be able to fail: a new guard or rule is proven by a deliberate violation, then reverted.
- Every behaviour change has a test that fails without it. Never weaken or delete an assertion to make a build pass.

### 6. Operations — every one-time action is written down

- **Where:** everything caused by tax-planning work, past and future, in any module it touches (`taxplanning`,
  `taxengine`, `portfolio`, `shared`, …), in `docs/taxplanning-build/post-deployment-runbook.md`; work unrelated to
  tax planning in `docs/charges-engine/production-runbook.md`. Each holds, in order, what a database needs because of a change:
  prechecks, startup runners to confirm, seed calls, data corrections, flags, recurring operations.
- **Same commit:** a change that needs any of these adds its entry there in the same commit, or it is not done.
  Each entry gives when, who, the exact command, a read-only check before and after, whether a second run is safe,
  and the way back (backup first for any write to stored data).
- **The owner runs it:** a Claude Code session never runs anything against a real database. It writes the command;
  the owner runs it and the result goes into the runbook's run log.
- **Idempotent, and removed after use:** every such operation can be run again — including after a run that failed
  part-way — and either completes the work or changes nothing, proven by a test. One meant to run once gets a
  removal ticket (backlog row and tracker issue) when it is introduced, done once its run is logged for every
  database.

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

# Format — spotless:check runs in `validate` and fails an unformatted build; apply fixes it
./mvnw spotless:apply
```

### Build gotchas

- **A failing test now fails the build.** `maven.test.failure.ignore` defaults to `false`; CI passes `-Dmaven.test.failure.ignore=true` on its own invocation so `verify` is still reached and the consolidated HTML report is generated. CI's surefire-XML gate (`failures="[1-9]"` / `errors="[1-9]"`, plus a minimum report count) is what fails the pipeline there.
- **Integration tests require Docker** and are tagged `integration` via `AbstractIntegrationTest` (Testcontainers `mongo:7.0` replica set). They still run by default; use `-Punit` to exclude them for a fast loop or when Docker is unavailable.
- **Tests run in parallel by class** (`backend/src/test/resources/junit-platform.properties`); methods within a class share a thread. `AbstractIntegrationTest` is `@Isolated` — every integration class runs alone, because RestAssured's `baseURI`/`port` are static and all integration classes share one Mongo container. Do not remove that annotation.
- **Coverage** is measured by JaCoCo (`backend/target/site/jacoco/`). The `check` gate is scoped to `com.thiru.wealthlens.brokercharges.engine*` at 90% line / 85% branch, so it enforces only where intended rather than failing globally.
- **Money is never asserted with a bare `assertEquals`.** Use `com.thiru.wealthlens.testsupport.MoneyAssert` — exact double comparison on rupee amounts is a latent failure.
- **`ArchitectureTest`** encodes the conventions below as ArchUnit rules (no field injection, controllers do not reach repositories, snake_case `@Field` names, no console printing). Introducing a violation fails the build.
- **Checkstyle fails the build** (10.x, `validate`, main and test sources) with the rule set in `checkstyle.xml` — the enforced half of "Engineering standards". Code that predates it is listed in `checkstyle-suppressions.xml` between the `BASELINE` markers; that list may only shrink (`CheckstyleBaselineTest`). Fix a violation rather than suppressing it. `ArchitectureTest` adds the naming rules (no generic class suffixes, no duplicate simple class names).
- **Claude Code sessions cannot commit a red tree:** `.claude/hooks/pre-commit-gate.sh` runs the unit tier before any `git commit` and blocks it on failure.
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
| `taxplanning` | `shared`, `auth`, `taxengine :: *`, `portfolio` — **CLOSED** |
| `taxengine` | `shared` — **CLOSED**; exposes only its named interfaces `input`, `result`, `money`, `id` |
| `insurance`, `finance` | `shared` |

`portfolio` is the domain center; `shared` depends *back* on `portfolio` because generic tools (XirrCalculator, ExcelBuilder, MongoConfig converters) reference portfolio types. The original eight modules are `OPEN`, so their internal sub-packages are still visible — a migration affordance from the old flat layout, not a design goal. The two tax modules are `CLOSED` (D-048): other modules see only a closed module's declared **named interfaces** (`@NamedInterface` on a sub-package's `package-info.java`; consumers write `"taxengine :: *"`). The rules vocabulary lives at `taxengine.rules` and may not depend on the rest of `taxengine` (`ArchitectureTest.rules_do_not_depend_on_the_runner`). Tax-planning design and build docs: `docs/taxplanning-design/00-BRIEF.md`, `docs/taxplanning-build/`.

Within every module: `controller/` (no business logic) → `service/` (`@Transactional`) → `repository/` (Spring Data MongoDB) → `entity/` + `dto/`.

### Cross-cutting behaviour to know before debugging responses

- `shared/advice/ResponseWrapperAdvice` wraps **every** JSON response body in `ApiResponse<T>` — except under the `integration-test` profile, where it is disabled (`@Profile("!integration-test")`). Integration tests therefore assert unwrapped payloads.
- `shared/exception/ControllerAdviser` maps `IllegalArgumentException` → 400, everything else → 500. `BadRequestException extends IllegalArgumentException`. Messages are human-readable strings, never codes.
- `auth/filter/AuthFilter` (a `OncePerRequestFilter`) validates the JWT before `UsernamePasswordAuthenticationFilter`; sessions are stateless, tokens expire in 30 min, HmacSHA256 signed with `app.security.key-secret`. Public paths are listed in `auth/config/AuthConfig` (`/auth/login`, `/auth/register`, `/helper/**`, `/finances/**`, `/template/**`, `/tax-planning/public/**`).
- MongoDB multi-document `@Transactional` writes need a replica set **and** `app.mongodb.transactions-enabled=true`.

### Domain rules that are not obvious from the code

**Transaction flow** — before processing, `PortfolioService` checks for existing temporary transactions and whether a corporate action blocks the trade. `FILTERABLE_CORPORATE_ACTIONS` (BONUS, DEMERGER, STOCK_SPLIT) block new transactions: the transaction is saved with `status = TEMPORARY` and its original `AssetRequest` is kept in the `assetRequest` field. Redrive with `POST /temporary-transactions/user/{email}/redrive`; `sourceTempTransactionId` links the processed transaction back to its original.

**Two live buy/sell flows.** `addTransaction` (`POST /portfolio/user/{email}/transaction`) and `addTransactionV2` (`.../transaction/v2`) coexist in `PortfolioService`, both served by `PortfolioController`. Note the prefix: `/transactions/user/{email}` is `TransactionController`, which only *reads* transactions. **Both are in live use.** V2 is where new work goes — the charges engine is wired into V2 only — but V1 is not dead code and must not be treated as such. V2 creates a separate `AssetEntity` per buy (no same-day lot merging), records broker charges into P&L for EQUITY, and sells via `findEligibleHoldingsForSell(...)` FIFO over lots with `transactionDate ≤ sellDate`. Changes to sell/cost-basis logic usually need to be made in both.

**Corporate actions** are processed per calendar quarter; `LastlyPerformedCorporateAction` records the last processed action per user/stock/asset-type/action-type/broker to prevent duplicates and drive blocking.

**Broker charges** — a **rules engine**, not fixed columns. A `ChargeScheduleEntity` (rate card) is validity-windowed per broker and scoped by asset type, segment, exchange and plan; it holds a list of `ChargeRule`s, each with a `ChargeBasis` (TURNOVER, FLAT, PER_UNIT, SLAB, SCOPED_FLAT, DERIVED, FORMULA) served by one calculator. `ChargeScheduleResolver` picks the card by specificity, `ChargeEngine` evaluates the rules in order, and `UserChargeService` records a `UserChargeEntity` per transaction — one row keyed uniquely on `{email, transaction_id}`, carrying both the ordered `lines` and a `amount_by_code` map. Charge codes are validated against a seeded `charge_catalogue`, and a code becomes a Mongo field name so `ChargeCodes` restricts it to `[A-Z][A-Z0-9_]*`. Scheme-level charges (mutual-fund exit load) live on `ChargeInstrumentEntity` and apply **per FIFO lot**. AMC bills from `charge_accounts` via `POST /charges/amc/impose`. Seed data is JSON under `resources/data/charges/` and enters a database only through `POST /charges/seed` (`SUPER_USER`) — never at startup.

**Charge engine flags** (`app.charges.*`, all in the three profile yamls): `engine-enabled` is a **master kill switch** — false makes simulate, backfill and the AMC cycle answer 503 and stops shadow recording. `shadow-recording` makes the trade path price and record every V2 trade with the result ignored. `authoritative` makes the computed total the cost basis on a V2 buy. The last two ship `false`, so the engine is inert until switched on.

**P&L** — `ProfitAndLossEntity` carries two parallel hierarchies: capital gains (`RealisedProfits → FinancialReport → MonthlyReport → FortnightReport`) and charges (`RealisedProfits → YearlyChargeSummary → MonthlyChargeSummary → half-month buckets`). The charge side is keyed by charge code rather than by fixed columns, so adding a charge is a data change; it is written only when a computation is handed in, which is the V2 flow.

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
