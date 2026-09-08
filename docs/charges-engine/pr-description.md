Phase A of the charges engine, complete: a standalone replacement for the broker-charges implementation, built alongside the existing one. It has an API of its own and nothing in the live trade path calls it — `portfolio/` is untouched, and `git diff master --stat -- backend/src/main/java/com/thiru/wealthlens/portfolio/` is empty. That is Phase A's exit criterion and what keeps the eventual cutover reversible.

## The problem

A rate card was a fixed set of Java fields, repeated across three classes. Adding one charge was an eight-file change across two modules. There was no asset-type dimension, so mutual funds, bonds and gold bonds accrued nothing. GST was parsed from a CSV string and applied over a merged bucket that included STT and stamp duty, which are not taxable services.

A rate card is now a list of rules evaluated by strategies chosen per basis, so adding or repricing a charge is a data change.

## Defects fixed

| | |
|---|---|
| **D1** | GST applied over STT and stamp duty. On a ₹1,00,000 sell it billed ₹21.00 against a correct ₹3.00 — **₹18.00 of overcharge on one trade**. A derived rule must now name its base codes explicitly, and a golden fixture freezes both numbers |
| **D6** | Publishing a rate card threw unless the incumbent was manually closed first. Publishing now supersedes, in the same transaction (AC-8) |
| **D7** | A rule with both a rate and a flat amount silently returned zero, so a mispriced card looked like free trading. Now rejected at write time and refused by the engine |
| **D9** | DP deduplication depended on read-your-own-write ordering over a `List`. Now an indexed `exists` query |

**D10** — depository charges undercounted across account holders — was fixed ahead of this branch in #60, because users were being undercharged then rather than after the cutover. The new design carries the same key.

Writing the integration tier found two more, both of the kind no unit test can see:

| | |
|---|---|
| **D11** | Every charges endpoint was **public**. `AuthConfig`'s chain ends in `anyRequest().permitAll()`, so a prefix nobody lists is open — including one exposing a user's whole trading history. Now authenticated, and a test asserts an unauthenticated request is refused |
| **D12** | Publishing a rate card and running the AMC cycle were open to **any authenticated user**. Publishing reprices every user's trades; `/charges/amc/impose` bills real money across every account. Both now require `SUPER_USER`, matching the tax-planning policy endpoints |

## What is here

Seven calculators behind one strategy interface; an orchestrator applying aggregator → floor/cap → rounding once per line, in that order and never inside a calculator; two resolvers with specificity ranking and caching; a write-time validator; seven services; twelve catalogue codes, six seeded rate cards and two seeded scheme profiles; a code-keyed reporting model; four controllers and eleven documented requests in `api-collection/`.

`POST /charges/simulate` is the one worth calling out. It prices a trade and records nothing — structurally, not by promise: the service holds the engine and no repository, and a test reads the class's own fields and fails if that stops being true. It makes the whole engine exercisable without a portfolio to mutate.

**AC-6 is now closed.** Two scheme profiles are seeded — one exit load graded by holding period, one expressed as the predicate `#holdingDays < 7`, both priced per FIFO lot. A redemption drawn from lots of different ages charges the young ones alone; averaging over the transaction would be wrong by the entire charge rather than by a rounding error. The AMC card is seeded too, unscoped because the cycle context carries no scrip, quantity or asset type and a card declaring any of those is disqualified by the resolver.

**764 tests** across both tiers. **99% mutation score** (475/476) across the engine and the new services, the single survivor being a known equivalent mutant; both JaCoCo gates green.

Four test tiers do more than check examples:

- **Golden contract notes (16)** — whole trades priced end to end against the shipped cards, with expected figures computed by hand *before* the engine was run. Asserted line by line as well as in total, because a right total can hide two compensating errors. The zero-charge fixtures also assert *why* nothing was charged, so a free redemption cannot be confused with a scheme profile that failed to load — checked by hiding the profiles and watching them go red.
- **Invariants (10 properties × 200 generated cards)** — relationships that must hold whatever a card says. Verified non-vacuous by reintroducing D1 and confirming two properties fail.
- **The extensibility guarantee (3)** — every charge in `ChargeExtensibilityTest` is `SYNTHETIC_LEVY_FOR_TEST`, a code that exists in a catalogue row and a rate card and **nowhere in Java**. It is computed, recorded and aggregated; a `DERIVED` rule can name it in its base; repricing it applies only after the boundary. If anyone later reaches for a switch on charge code, these fail. That is the design promise stated in a way the build can defend.
- **Mutation testing** — which found a dead negative-zero guard, five unkillable mutants that were one untested warning, a zero-price boundary documented in a comment and asserted nowhere, and several fields written but never asserted. `testsupport/LogCapture` exists because a branch that only logs is otherwise indistinguishable from one that was deleted.

## Also in this branch

- **pitest was silently broken on Java 25** (`Unsupported class file major version 69`), so the mutation profile had never run on this toolchain. Bumped to 1.20.3.
- **Both quality gates widened** to cover `brokercharges.service`, judged per class. The superseded implementation is included rather than exempted — it was assumed it would fail the bar and it does not.

## Known and deliberate

- **Rates are placeholders.** Every card carries `sourceUrl` and a null `verifiedOn`, and a test asserts that state. **AC-2 stays open** until a human compares each figure against the broker's published page; `GET /charge-schedules/unverified` lists exactly those cards. **Scheduled for staging after this merges** (ADR-18), so it does not gate the merge — `docs/charges-engine/staging-runbook.md` is the curl-by-curl procedure.
- **Exit load cannot be exercised through the API.** `ChargeSimulationRequest` carries no FIFO lots, so a `perLot` rule evaluates zero times and `/charges/simulate` returns ₹0 exit load however the profile is written. The charge itself is asserted by the golden fixtures and the integration tier, both of which reach the engine directly; the caller that supplies lots is Phase B.
- **The resolver cache is evicted only by the publish path.** A rate card written straight to the repository is invisible to the engine until something evicts it.
- The old implementation is intact and still live, including `/broker-charges/amc/impose` — which is *not* the same endpoint as the new one, and running both against one period would charge twice. It is deleted in Phase C, not before.

## Review guide

`docs/charges-engine/README.md` §11 is the narrative and carries the Phase A gate item by item; `implementation-checklist.md` is the tracker, current through Chunk 9, with every open box saying why it is open. Acceptance criteria are signed off there with named evidence.

The two commits worth reading closely are the API surface and the integration tier — the first because it decides the shape callers will live with, the second because it is where the authorisation gaps surfaced.

One more worth a glance: the scheme-profile commit widened `ChargeScheduleValidator` to cover instrument profiles. Validating only the broker's card had left exit load — the one charge a rate card cannot express — entirely unchecked, and it is the rule most worth checking, because a mistake in something conditioned on a holding period is invisible on every redemption the condition excludes.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
