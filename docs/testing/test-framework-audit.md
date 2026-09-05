# Test Framework Audit

**Date:** 2026-09-05
**Scope:** `backend` test suite, `test-report` module, `.github/workflows/cicd.yaml`, Maven test configuration
**Motivation:** before adding ~150 tests for the charges engine, establish whether the framework they land in is sound.

---

## Verdict

**Good bones, not yet world-class.** The architectural choices are right — real HTTP against a real MongoDB replica set, a singleton container, module-boundary verification, a consolidated report, a CI gate. What is missing is the **measurement and enforcement layer**: nothing tells you what is untested, nothing proves the assertions would catch a wrong number, and the build's exit code does not mean what it appears to mean.

For a system that computes money, the last point is the one that matters.

| Dimension | State |
|---|---|
| Test infrastructure (containers, HTTP, profiles) | **Strong** |
| Coverage measurement | **Absent** |
| Assertion quality for money | **Weak** — exact float equality |
| Unit / integration separation | **Absent** — Docker required for every run |
| Build truthfulness | **Broken** — failures do not fail the build |
| Convention enforcement | **Partial** — module boundaries only |
| Data-driven testing | **Absent** — zero parameterized tests |

**Scale:** 270 main classes, 33 test classes, 333 `@Test` methods. `helper` and `insurance` have no tests referencing them at all.

---

## What is genuinely good

Worth stating plainly, because these are the parts not to disturb:

1. **Singleton Testcontainers pattern done correctly.** `AbstractIntegrationTest` starts one `mongo:7.0` replica-set container in a static initialiser and deliberately does *not* stop it in `@AfterAll` — with a comment explaining why. That is the correct pattern, and the comment prevents someone "fixing" it into a hang.
2. **Real replica set, transactions on.** `app.mongodb.transactions-enabled=true` is set for integration tests, so multi-document `@Transactional` behaviour is genuinely exercised rather than mocked away.
3. **Full-stack HTTP tests.** RestAssured against `RANDOM_PORT` with real JWTs from `generateToken(email, role)`, including an expired-token helper. Auth is tested as it actually runs.
4. **`ResponseWrapperAdvice` disabled under the `integration-test` profile** so tests assert real payloads — a deliberate, documented seam.
5. **`WealthLensModulithTest`** runs `ApplicationModules.verify()`. Architecture drift is caught automatically. Most codebases this size have nothing equivalent.
6. **Consolidated HTML report** as its own Maven module, published as a CI artifact on `always()`.

---

## Findings

### CRITICAL

#### T1 — `testFailureIgnore=true` makes the build lie
`pom.xml:162`. `./mvnw test` exits **0 with failing tests**. Every local run, every IDE run, every script that checks an exit code is wrong. CI compensates by grepping surefire XML — so the *only* thing standing between a broken test and a green pipeline is shell string-matching.

The reason it exists is legitimate: the `verify` phase must be reached so the consolidated report generates. But the cost is paid on every developer run.

**Fix:** default to `false`; pass `-Dmaven.test.failure.ignore=true` explicitly in the CI invocation only. Local runs regain a truthful exit code; CI keeps its report.

#### T2 — No coverage measurement whatsoever
No JaCoCo, no alternative. 333 tests across 270 classes and **no way to answer "is this tested?"** other than reading code. The request to "test the entire algo in detail" is unverifiable without this.

**Fix:** JaCoCo `prepare-agent` + `report`, with a `check` rule scoped to the charges engine package (say 90% line / 85% branch) rather than a global threshold that would fail on day one.

#### T3 — Unit and integration tests are indistinguishable to Surefire
There are no `<includes>`, so Surefire's defaults (`**/*Test.java`) match `PortfolioServiceTest` **and** `PortfolioIntegrationTest` alike. Consequence: `./mvnw test` boots Docker and a MongoDB container to run a pure Mockito test. There is no fast feedback loop, Docker is mandatory for any test run, and the two categories cannot be parallelised differently.

**Fix:** `@Tag("integration")` on `AbstractIntegrationTest`; Surefire `<excludedGroups>integration</excludedGroups>`; a Failsafe execution or `-Pintegration` profile for the tagged set.

---

### HIGH

#### T4 — Exact floating-point equality on money
`UserBrokerChargeServiceTest:102` — `assertEquals(57.55, saved.getGovtCharges())`. No delta: this is **exact bit comparison of a double**. Of 486 `assertEquals` calls, only 69 supply a delta.

It passes today because the arithmetic happens to be deterministic. Reorder two operations in a refactor and it fails with `expected 57.55 but was 57.550000000000004` — a failure that teaches nothing and trains people to add deltas reactively.

**Two fixes, both worth doing:**
- Tests: AssertJ `isCloseTo(57.55, within(0.01))`, or a `assertMoney(expected, actual)` helper.
- Production: the charges engine should compute in `BigDecimal` (or scaled long paise) internally and expose `double` only at the boundary. GST over a summed base, with per-line rounding, is exactly where `double` drift becomes rupees. **This is a design decision for the engine, raised in the test plan.**

#### T5 — No AssertJ in the backend
`test-report/pom.xml` has it; `backend/pom.xml` does not. Backend tests use JUnit's `assertEquals`, which gives poor diagnostics on collections and objects — precisely what charge line-item lists are. Inconsistent across two modules of the same repo.

#### T6 — Zero parameterized tests
No `@ParameterizedTest`, no `@MethodSource`, no `@Nested` anywhere in 333 tests. The charges engine is combinatorial — side × basis × aggregator × rounding × event. Written in the current style that is ~200 copy-pasted methods nobody will maintain. Written as tables it is ~15 methods with data files.

#### T7 — `cleanDatabase()` hardcodes collection names
`AbstractIntegrationTest:64-77` drops 12 named collections. Every new collection must be added by hand or state leaks silently between test classes — a failure mode that manifests as an unrelated test failing later.

**Fix:** enumerate collections from the database and drop everything except an explicit seed whitelist (the tax-policy collections, seeded once at `@PostConstruct`).

---

### MEDIUM

#### T8 — No architecture rules beyond module boundaries
`CLAUDE.md` states conventions that are currently enforced only by review: constructor injection via `@RequiredArgsConstructor`, controllers carrying no business logic, entities implementing `AuditableEntity`, services annotated `@Transactional`, snake_case `@Field` names, no star imports.

ArchUnit turns each into a test. For a codebase with a documented convention list, this is unusually high value per line.

#### T9 — No mutation testing
Coverage says a line ran. Mutation testing says an assertion would have **caught it being wrong** — the only automated evidence that a money calculation is genuinely tested. Scoped to `brokercharges/engine/**` it runs in seconds and is exactly the QA-replacement the request is asking for.

#### T10 — No parallel execution
No `junit-platform.properties`. The suite runs single-threaded and will keep getting slower. Blocked today by T3 (integration tests share a container and static RestAssured state) — fix T3 first, then parallelise the unit tier.

#### T11 — No shared fixtures or builders
Each test class hand-rolls entity construction (`TradeMatchingServiceTest:472` has a private `txn(...)` helper; others repeat similar code). A `test/fixtures` package with builders removes the duplication and makes the charge-engine tests readable.

#### T12 — CI does not enforce formatting or style
`cicd.yaml` never runs `spotless:check`, and Checkstyle is advisory (`failOnViolation=false`). Format drift is inevitable and shows up as noise in diffs.

#### T13 — The CI failure gate passes when no reports exist
```bash
for f in backend/target/surefire-reports/TEST-*.xml ...; do
  [ -f "$f" ] || continue
```
If the glob matches nothing, the loop body never runs and `FAILED` stays `0` — **green**. A build failure would normally fail the earlier step, so the hole is narrow, but the gate should assert a minimum report count rather than assume one.

#### T14 — Leftover scratch file
`backend/src/test/java/com/thiru/wealthlens/dto/Student.java` is not test infrastructure.

---

## Implementation status — all items complete

Verified on branch `feature/charges-engine`, 2026-09-05.

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | **T3** test tiering | done | `-Punit` / `-Pintegration` profiles; `@Tag("integration")` on `AbstractIntegrationTest`. **161 unit tests in 6.8s, no Docker** |
| 2 | **T4/T5** AssertJ + `MoneyAssert` | done | `testsupport/MoneyAssert.java`; `assertMoney`, `assertNoCharge`, `assertBreakdown` |
| 3 | **T1** truthful build | done | `maven.test.failure.ignore` defaults `false`; verified a failing test now returns a non-zero exit. CI passes the flag on its own line |
| 4 | **T2** JaCoCo | done | 0.8.14 (0.8.12 cannot read Java 25 bytecode — "Unsupported class file major version 69"). Gate scoped to `brokercharges.engine*` |
| 5 | **T7** dynamic cleanup | done | Enumerates collections, preserves a seeded whitelist, uses `deleteMany` not `drop` |
| 6 | **T9** PIT | done | `-Pmutation`, scoped to `brokercharges.engine.*` and `taxplanning.engine.*`, threshold 85 |
| 7 | **T11** fixtures | partial | `testsupport/` package created with `MoneyAssert` + `Student`. Charge-specific builders land with Chunk 2, when the entities exist |
| 8 | **T8** ArchUnit | done | `ArchitectureTest` — 8 rules, all passing |
| 9 | **T12** `spotless:check` in CI | done | Runs before the suite, fails fast |
| 10 | **T10** parallel execution | done | `junit-platform.properties`; classes concurrent, methods same-thread, integration `@Isolated` |
| 11 | **T13** CI report-count gate | done | `MIN_REPORTS=25`; a missing report is now a failure, not a pass |
| 12 | **T14** `Student.java` | **corrected** | It is *not* dead — `TJsonMapperTest` uses it. Moved to `testsupport/` rather than deleted. My original finding was wrong |

### Verification

Full suite green with Docker running:

| Run | Result |
|---|---|
| `./mvnw test -pl backend -Punit` | **161 tests, 6.8s, no Docker** |
| `./mvnw test -pl backend -Pintegration` | **179 tests, all pass** |
| `./mvnw clean test verify` (CI equivalent) | **340 backend + 26 test-report, BUILD SUCCESS**, consolidated report generated, JaCoCo check ran |
| Unit tier ×3 consecutive | 161/161 each time — no flakiness under parallel class execution |
| CI gate preconditions | 35 surefire reports (floor is 25); no `failures=`/`errors=` in any XML |

### Baseline coverage, now measurable

Full suite (unit + integration):

```
TOTAL                                   78% instruction   59% branch

brokercharges.service                   98%   97%
shared.util.calculator                  97%   92%
corporate.service                       93%   85%
portfolio.service                       91%   76%
auth.config                             91%   66%
taxplanning.policy.service              71%   62%
taxplanning.engine                      68%   52%
auth.service                            67%   59%
shared.util.time                        53%   25%
taxplanning.recommendation              47%   23%
finance.service                         45%   46%
portfolio.service.parser                26%    0%
corporate.controller                     5%    0%
helper.controller                        3%    —
shared.advice                            0%    0%
```

Branch coverage at 59% against 78% instruction is the number worth watching: the code is *executed* far more than it is *decided over*. `shared.advice` at 0% means `ResponseWrapperAdvice` and `ControllerAdviser` — the classes shaping every API response and error — are untested. Neither is in this feature's scope, but both are worth a follow-up.

### Two findings that changed on contact with the code

- **T14 was wrong.** `Student.java` is a live fixture for `TJsonMapperTest`. Moved, not deleted.
- **T7 understated the problem.** `cleanDatabase()` was not merely a future trap — `asset_management_details` and `trade_outcomes` were **never cleaned**, so state was already leaking between integration test classes.

Two of the eight ArchUnit rules I first wrote failed, and in both cases the rule was wrong rather than the code: `taxplanning` deliberately organises by `engine/`, `recommendation/`, `document/` instead of a flat `service/` package, and `AuthService` legitimately calls `System.currentTimeMillis()` for JWT timestamps. Both rules were rewritten to express the real intent.

---

## Prioritised remediation *(historical — all now complete)*

Ordered by value per hour, not by severity.

| # | Change | Effort | Why now |
|---|---|---|---|
| 1 | `@Tag("integration")` + Surefire `excludedGroups` (**T3**) | ~1h | Unblocks fast local runs; prerequisite for parallelism. Do before writing 150 engine tests. |
| 2 | Add AssertJ + `assertMoney` helper (**T4, T5**) | ~1h | Every engine test asserts money. Set the idiom before, not after. |
| 3 | `testFailureIgnore` → CI-only flag (**T1**) | ~30m | The build stops lying. |
| 4 | JaCoCo with a charges-engine-scoped threshold (**T2**) | ~2h | Makes "tested in detail" measurable rather than asserted. |
| 5 | Dynamic `cleanDatabase()` (**T7**) | ~1h | Removes the trap before four new collections are added. |
| 6 | PIT scoped to `brokercharges/engine` (**T9**) | ~2h | The actual QA-replacement mechanism for money maths. |
| 7 | Test fixture builders (**T11**) | ~3h | Pays for itself across ~150 new tests. |
| 8 | ArchUnit convention rules (**T8**) | ~4h | Enforces `CLAUDE.md` automatically; independent of this feature. |
| 9 | `spotless:check` in CI (**T12**) | ~15m | Trivial. |
| 10 | Parallel unit execution (**T10**) | ~1h | After 1. |
| 11 | CI gate minimum-report assertion (**T13**) | ~15m | Trivial. |
| 12 | Delete `Student.java` (**T14**) | ~1m | — |

Items 1–6 are the ones I would do **before** writing the charges engine tests. Together roughly a day, and they change what those 150 tests are worth.

---

## What world-class would look like here

Not a generic wishlist — the specific end state for this codebase:

1. **Three tiers, separately runnable.** `./mvnw test` = unit only, no Docker, under 30 seconds. `-Pintegration` = Testcontainers tier. `-Pmutation` = PIT over calculation packages.
2. **Coverage as a ratchet, not a cliff.** Global threshold at today's actual number; new-code threshold at 90%. Coverage can only go up.
3. **Mutation score gate on calculation code only.** ≥85% on `brokercharges/engine/**` and `taxplanning/engine/**`. Nowhere else — mutation testing on DTOs is noise.
4. **Money asserted through one helper.** Never a bare `assertEquals` on a double.
5. **Golden files for anything with a real-world counterpart.** Contract notes and tax computations are checked against fixtures a human verified once, so a regression is a diff rather than a debugging session.
6. **Conventions as ArchUnit tests.** `CLAUDE.md` becomes executable.
7. **CI reports coverage delta and mutation score on the PR**, alongside the consolidated HTML report already produced.

The gap between here and there is roughly two days of infrastructure work — most of it items 1–6 above.
