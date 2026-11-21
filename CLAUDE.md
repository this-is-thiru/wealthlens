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
  Existing violations are listed in `docs/taxplanning-build/reviews/2026-10-21-law-in-code-audit.md`
  (`W-088l`), each placed as a row; a new one is a defect.

### 5. Tests

- Expected values are derived **independently of the code** (from the source, by hand, shown) — never by running the implementation and copying its output.
- A test must be able to fail: a new guard or rule is proven by a deliberate violation, then reverted.
- Every behaviour change has a test that fails without it. Never weaken or delete an assertion to make a build pass.

### 6. Operations — every one-time action is written down

- **Where:** one deploy runbook, `docs/taxplanning-build/post-deployment-runbook.md`, for every change in any module:
  tax planning, the charge engine and the trade ledger (W-176, D-379). It holds, in order, what a database needs
  because of a change: prechecks, startup runners to confirm, clearing, seed calls, data corrections, flags, recurring
  operations.
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

# Run the app (default profile → wealthlens-dev Atlas cluster, -Dspring-boot.run.profiles=staging|prod for the others; needs MONGO_USER, MONGO_PASSWORD, JWT_SECURITY_KEY, TAX_DATA_KEY, TAX_DIGEST_KEY, PRIVACY_CONTACT — the two tax keys 32 bytes in base64)
./mvnw spring-boot:run -pl backend

# All tests (unit + integration; integration needs Docker)
./mvnw test -pl backend

# Unit tier only — no Docker, ~160 tests in ~7s. The fast inner loop.
./mvnw test -pl backend -Punit

# Integration tier only (module-level tests against Testcontainers Mongo)
./mvnw test -pl backend -Pintegration

# Mutation testing — scoped to the calculation engines only
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage -Pmutation -pl backend

# Performance budgets (PRD §5 N1 – N3; W-143) — machine-dependent, so never in the default build; no coverage agent
./mvnw test -pl backend -Pbenchmark

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
- **Integration tests require Docker** and are tagged `integration` via `AbstractIntegrationTest` (Testcontainers `mongo:8.0.4` replica set — pinned: later 8.0 patches refuse to start on Linux 6.19+, SERVER-121912, which CI's Ubuntu 26 runner has). They still run by default; use `-Punit` to exclude them for a fast loop or when Docker is unavailable.
- **Tests run in parallel by class** (`backend/src/test/resources/junit-platform.properties`); methods within a class share a thread. `AbstractIntegrationTest` is `@Isolated` — every integration class runs alone, because RestAssured's `baseURI`/`port` are static and all integration classes share one Mongo container. Do not remove that annotation.
- **Coverage** is measured by JaCoCo (`backend/target/site/jacoco/`). The `check` gates run at `verify` and are scoped where they are meant to bite rather than failing globally: `com.thiru.wealthlens.brokercharges.engine*` at 90% line / 85% branch; `taxengine` outside `rules` at 95% line and `taxengine.rules` at 90% line (`DR 07` X5.5), with **branch minimums at X5.5's 90% and 85%** (W-144 measured 81% and 76%; W-240a – d closed the gap) — never lower them. `-Pmutation-taxengine` holds the cap shapes and marginal reliefs to a 90% PIT score (100% at W-144).
- **Money is never asserted with a bare `assertEquals`.** Use `com.thiru.wealthlens.testsupport.MoneyAssert` — exact double comparison on rupee amounts is a latent failure.
- **`ArchitectureTest`** encodes the conventions below as ArchUnit rules (no field injection, controllers do not reach repositories, snake_case `@Field` names, no console printing). Introducing a violation fails the build.
- **Checkstyle fails the build** (10.x, `validate`, main and test sources) with the rule set in `checkstyle.xml` — the enforced half of "Engineering standards". Code that predates it is listed in `checkstyle-suppressions.xml` between the `BASELINE` markers; that list may only shrink (`CheckstyleBaselineTest`). Fix a violation rather than suppressing it. `ArchitectureTest` adds the naming rules (no generic class suffixes, no duplicate simple class names).
- **Docs:** `docs/README.md` is the index. Decisions (D-rows) and blockers live in `docs/taxplanning-build/DECISIONS.md`; `STATE.md` holds the status board and current position; the history removed by W-357a is in git tag `docs-history-m1`.
- **`FinishedPlanTest` fails the build** when a plan in `docs/taxplanning-build/plans/` belongs to an item the STATE.md status board marks `D` or `—` but still has an open `- [ ]` step that does not say "not taken" (W-200, D-399). Tick the step with the commit that did it, or mark it "not taken" with why.
- **The commit gate checks standards, not tests:** `.claude/hooks/pre-commit-gate.sh` runs `./mvnw validate` (Checkstyle and `spotless:check`, a few seconds) before any `git commit` from a Claude Code session and blocks it on failure; `.claude/hooks/pre-commit-gate-test.sh` tests it. It runs no test — the pipeline runs every tier, so a failing test is found there (W-267, D-534). `.claude/hooks/unit-tier.sh` runs the unit tier by hand at low priority (JUnit parallel factor 0.5).
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

`portfolio` is the domain center; `shared` depends *back* on `portfolio` because `XirrCalculator`, `QueryFilter` and the asset and transaction prototypes in `shared/dto` reference portfolio types. The original eight modules are `OPEN`, so their internal sub-packages are still visible — a migration affordance from the old flat layout, not a design goal. The two tax modules are `CLOSED` (D-048): other modules see only a closed module's declared **named interfaces** (`@NamedInterface` on a sub-package's `package-info.java`; consumers write `"taxengine :: *"`). The rules vocabulary lives at `taxengine.rules` and may not depend on the rest of `taxengine` (`ArchitectureTest.rules_do_not_depend_on_the_runner`). Tax-planning design and build docs: `docs/taxplanning-design/00-BRIEF.md`, `docs/taxplanning-build/`.

Within every module: `controller/` (no business logic) → `service/` (`@Transactional`) → `repository/` (Spring Data MongoDB) → `entity/` + `dto/`.

### Cross-cutting behaviour to know before debugging responses

- **Responses are plain JSON, the same in tests and production** (W-359a): no wrapper. **Every error is RFC 9457 `ProblemDetail`** (`application/problem+json`: `status`, `title` = the status's reason, `detail` = the message, `instance` = the path), built by `shared/exception/ControllerAdviser`. Actuator serves only `/actuator/health` (and its readiness probe), for `SUPER_USER`. The OpenAPI spec, generated from the controllers by springdoc, is `GET /api/v1/admin/docs/openapi` for `SUPER_USER`, with no Swagger UI, since a browser cannot carry the token (W-359b); `shared/web/ApiDocs` keeps `@CallerEmail` out of it and documents a page as `page` and `size`. **Logs** (W-358d1): every answer carries `X-Request-Id`, an id the app makes (`shared/web/RequestIdFilter`, first of all filters); every line logged while serving that request prints it, and `privacy_processing_log` rows store it as `request_id`; `logback-spring.xml` writes every email as `[email]` and every PAN masked, in messages and stack traces (`EmailLogMaskTest`, `PanLogMaskTest` read the shipped file). `ApiCollectionContractIntegrationTest` fails the build when an api-collection request names a route, or a body field, the spec lacks — so the pipeline checks out the submodule.
- `shared/exception/ControllerAdviser` maps `IllegalArgumentException` → 400, and a request Spring could not bind to the caller's status: a value of the wrong type or a missing parameter, upload or unreadable body → 400, a wrong method → 405, a wrong content type → 415 (W-263). A trade that collided with another on every attempt → 409 (`TradeCollisionException`; `TradeCollisionRetry` runs a trade again on Mongo's `TransientTransactionError`, W-269). Everything else → 500. A tax year no pack ships for is an `IllegalArgumentException` from `TaxCalculator`; `PackValidationException` stays a malformed pack, 500. `BadRequestException extends IllegalArgumentException`. Messages are human-readable strings, never codes.
- **Every route is `/api/v1/{public|me|admin}/{module}/…`** (W-358c1, D-855): `shared/web/ApiZones` adds the prefix from the controller's zone annotation (`@PublicApi`, `@MeApi`, `@AdminApi` — exactly one per controller, `ApiZoneTest`) and its package's module (`auth`, `charges`, `corporate-actions`, `finances`, `templates`, `portfolio`, `privacy`, `tax`). A `/me` route takes its user from the token (`@CallerEmail`), never from the path; an admin route acting on one user names them (`/users/{email}/…`). `AuthConfig`: `/api/v1/public/**`, `/privacy` (the notice as a page, W-247) and `/error` (where a refusal is rendered, W-359a) are open; `/api/v1/me/**` needs a login; `/api/v1/admin/**` and `/actuator/**` need `SUPER_USER`; **anything else is denied** (W-358a). `RouteAccessIntegrationTest` reads every route the app serves and fails the build if one sits outside the zones, answers without a login outside public, lets a user's token through on admin, or names a user under `/me`.
- Spring's resource server reads the access token (W-358b): `auth/config/AccessTokenConfig` holds the JWT encoder and decoder (HS256 on `app.security.key-secret`, a key id from the key's thumbprint, the expiry on the app's `Clock`, a held account refused at once), roles and sign-in factors in the `authorities` claim. Sessions are stateless; an access token lasts 15 min (`AccessTokens`); sign-in also returns a `refresh_token` (`RefreshTokens`, collection `refresh_tokens`: SHA-256 hash only, 14 days from the sign-in, rotated by `POST /api/v1/public/auth/token/refresh`, a reused token ends its whole family, ended by `POST /api/v1/public/auth/logout`, a password change and erasure); a public path ignores the `Authorization` header. A password is taken only at sign-in: HTTP Basic is off (W-361). Guessing is limited (W-358d2, `SignInAttempts`, settings `app.auth.sign-in-limit`): 5 failed sign-ins for one address from one client in 15 minutes, or 30 calls from one client to `/api/v1/public/auth/**`, answer 429 with `Retry-After`; the integration profile raises the per-client limit, since every test class calls from 127.0.0.1. Registration answers a taken and a held address alike ("This email cannot be registered").
- MongoDB multi-document `@Transactional` writes need a replica set **and** `app.mongodb.transactions-enabled=true`.

### Domain rules that are not obvious from the code

**Transaction flow** — before processing, `PortfolioService` checks for existing temporary transactions and whether a corporate action blocks the trade. `FILTERABLE_CORPORATE_ACTIONS` (BONUS, DEMERGER, STOCK_SPLIT) block new transactions: the transaction is saved with `status = TEMPORARY` and its original `AssetRequest` is kept in the `assetRequest` field. The temporary records the flow that received it (`trade_flow`, `TradeFlow.V1` for V1 and the upload, `V2` for V2; W-270), and redrive records it through that flow — one stored before W-270 has none and goes through V1. Redrive with `POST /api/v1/me/portfolio/temporary-transactions/redrive`; `sourceTempTransactionId` links the processed transaction back to its original.

**Two live buy/sell flows.** `addTransaction` (`POST /api/v1/me/portfolio/transaction`) and `addTransactionV2` (`.../transaction/v2`) coexist in `PortfolioService`, both served by `PortfolioController`. Note the prefix: `/api/v1/me/portfolio/transactions` is `TransactionController`, which only *reads* transactions. **Both are in live use.** V2 is where new work goes, but V1 is not dead code and must not be treated as such. The charges engine prices and records the V1 buy too; only V2 hands the computation to the P&L charge summary, makes it the cost basis, and prices a sale (V1's sale is not priced). V2 creates a separate `AssetEntity` per buy (no same-day lot merging), records broker charges into P&L for EQUITY, and sells via `findEligibleHoldingsForSell(...)` FIFO over lots with `transactionDate ≤ sellDate` — V1's sale reads the same lots since W-271. Changes to sell/cost-basis logic usually need to be made in both.

**Corporate actions** are processed per calendar quarter; `LastlyPerformedCorporateAction` records the last processed action per user/stock/asset-type/action-type/broker to prevent duplicates and drive blocking.

**Broker charges** — a **rules engine**, not fixed columns. A `ChargeScheduleEntity` (rate card) is validity-windowed per broker and scoped by asset type, segment, exchange and plan; it holds a list of `ChargeRule`s, each with a `ChargeBasis` (TURNOVER, FLAT, PER_UNIT, SLAB, SCOPED_FLAT, DERIVED, FORMULA) served by one calculator. `ChargeScheduleResolver` picks the card by specificity, `ChargeEngine` evaluates the rules in order, and `UserChargeService` records a `UserChargeEntity` per transaction — one row keyed uniquely on `{email, transaction_id}`, carrying both the ordered `lines` and a `amount_by_code` map. Charge codes are validated against a seeded `charge_catalogue`, and a code becomes a Mongo field name so `ChargeCodes` restricts it to `[A-Z][A-Z0-9_]*`. Scheme-level charges (mutual-fund exit load) live on `ChargeInstrumentEntity` and apply **per FIFO lot**. AMC bills from `charge_accounts` via `POST /api/v1/admin/charges/amc/impose`. Seed data is JSON under `resources/data/charges/` and enters a database only through `POST /api/v1/admin/charges/seed` (`SUPER_USER`) — never at startup.

**Charge engine flags** (`app.charges.*`, all in the three profile yamls): `engine-enabled` is a **master kill switch** — false makes `POST /api/v1/me/charges/simulate`, `GET /api/v1/admin/charges/charge-accounts/due` and `POST /api/v1/admin/charges/amc/impose` answer 503 (`ChargeEngineSwitch`), and makes the trade path record nothing while the trade still saves with its entered charges. `shadow-recording` makes the trade path price and record every V2 trade and every V1 buy. `authoritative` makes the computed total the cost basis on a V2 buy — only while `shadow-recording` is also on, since that is what computes it. All three ship `true` in `application.yaml`, `application-staging.yaml` and `application-prod.yaml`, so a V2 trade is priced as soon as the app is up: seed first (deploy runbook §1B).

**P&L** — `ProfitAndLossEntity` carries two parallel hierarchies: capital gains (`RealisedProfits → FinancialReport → MonthlyReport → FortnightReport`) and charges (`RealisedProfits → YearlyChargeSummary → MonthlyChargeSummary → half-month buckets`). The charge side is keyed by charge code rather than by fixed columns, so adding a charge is a data change; it is written only when a computation is handed in, which is the V2 flow.

**Tax planning** (`taxplanning`) — the `/api/v1/me/tax/**` flow: taxpayer profiles and their family, employers, landlords, lenders and bank accounts (`profile/`), tax-year workspaces and filing drafts (`workspace/`), and computations, the regime comparison, the optimiser and the PDF report, computed by `taxengine` from its rule packs (`compute/`). The older salary-profile flow under `/tax-planning/user/**`, its seeded policies (`data/tax-policies/`), engines, restructuring advice and PDF were removed by W-159 (D-726); `/api/v1/admin/tax/**` holds only the Gazette watch and the tax jobs run on request (`POST /api/v1/admin/tax/jobs/{name}/run`, W-336 — the jobs ship off while the app is local).

## Conventions

- `@RequiredArgsConstructor` for DI; injected fields are `private final`. No hand-written constructors.
- **No route answers with a stored `@Document`** (W-358c2, `ResponseContractTest`): a response type names its fields, so nothing stored only for the app — `auditMetadata`, internal keys — reaches a caller; nor does a route read its body into one (W-206, `RequestContractTest`): a request type names what a caller may send.
- **A list that grows answers a page** (W-358c3b): Spring's `Pageable` in, `PagedModel` out (`{"content", "page"}`), 50 by default, at most 200 (`spring.data.web.pageable`); the order is fixed by the service, newest first unless the list has its own.
- Services: `@Service @Log4j2 @Transactional @RequiredArgsConstructor`. Entities: `@Data @AllArgsConstructor @NoArgsConstructor @Document @Field`. DTOs: `@Data @NoArgsConstructor @ToString`; avoid `@Builder` on request DTOs.
- MongoDB: collections and fields are `snake_case` (`@Document(value = "transactions")`, `@Field("stock_code")`), id via `@MongoId`, entities implement `AuditableEntity` for `AuditMetadata`. Prefer Spring Data derived queries (`findByEmailAndStatus`).
- REST paths lowercase-hyphenated, under a zone and module (`/api/v1/me/portfolio/…`); a user's own data is reached through `/me`, never an email in the path. Dates `yyyy-MM-dd`, datetimes `yyyy-MM-dd'T'HH:mm:ss` via `@JsonFormat`. All times stored UTC via `TLocalDateTime.atUtc()`.
- Static utilities in `shared/util` are `T`-prefixed: `TCollectionUtil`, `TJsonMapper`, `TStringUtil`, `TOptional`, `TLocalDate`/`TLocalDateTime`/`TLocalTime`. Excel: `ExcelWorkbooks`, `ExcelSheet` and `ExcelColumn` (`shared/util/parser`) write workbooks; `ExcelParser` reads uploads. Dynamic Mongo queries: `QueryBuilder`/`QueryFilter`.
- 4-space indent, K&R braces, no star imports.
- Tests: `@ExtendWith(MockitoExtension.class)` with `@Mock`/`@InjectMocks`, `// Given` `// When` `// Then` sections, methods named `methodName_whenCondition_expectedResult`. Integration tests extend `AbstractIntegrationTest` (shared Testcontainers Mongo, `generateToken(email, role)` helper, per-test collection drops in `cleanDatabase()` — add any new collection there).

## Notes

- `api-collection/` is an OpenCollection-format HTTP request collection covering the API surface.
- The root `README.md` documents the data model in detail but predates the tax-planning build-out; trust the code over it.
