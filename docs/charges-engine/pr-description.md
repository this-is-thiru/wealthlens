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

Seven calculators behind one strategy interface; an orchestrator applying aggregator → floor/cap → rounding once per line, in that order and never inside a calculator; two resolvers with specificity ranking and caching; a write-time validator; seven services; twelve catalogue codes and five seeded rate cards; a code-keyed reporting model; four controllers and eleven documented requests in `api-collection/`.

`POST /charges/simulate` is the one worth calling out. It prices a trade and records nothing — structurally, not by promise: the service holds the engine and no repository, and a test reads the class's own fields and fails if that stops being true. It makes the whole engine exercisable without a portfolio to mutate.

**744 tests** across both tiers. `brokercharges.engine` at **99% mutation score** (256/257, 0 uncovered); both JaCoCo gates green.

Four test tiers do more than check examples:

- **Golden contract notes (12)** — whole trades priced end to end against the shipped cards, with expected figures computed by hand *before* the engine was run. Asserted line by line as well as in total, because a right total can hide two compensating errors.
- **Invariants (10 properties × 200 generated cards)** — relationships that must hold whatever a card says. Verified non-vacuous by reintroducing D1 and confirming two properties fail.
- **The extensibility guarantee (3)** — every charge in `ChargeExtensibilityTest` is `SYNTHETIC_LEVY_FOR_TEST`, a code that exists in a catalogue row and a rate card and **nowhere in Java**. It is computed, recorded and aggregated; a `DERIVED` rule can name it in its base; repricing it applies only after the boundary. If anyone later reaches for a switch on charge code, these fail. That is the design promise stated in a way the build can defend.
- **Mutation testing** — which found a dead negative-zero guard, five unkillable mutants that were one untested warning, a zero-price boundary documented in a comment and asserted nowhere, and several fields written but never asserted. `testsupport/LogCapture` exists because a branch that only logs is otherwise indistinguishable from one that was deleted.

## Also in this branch

- **pitest was silently broken on Java 25** (`Unsupported class file major version 69`), so the mutation profile had never run on this toolchain. Bumped to 1.20.3.
- **Both quality gates widened** to cover `brokercharges.service`, judged per class. The superseded implementation is included rather than exempted — it was assumed it would fail the bar and it does not.

## Known and deliberate

- **Rates are placeholders.** Every card carries `sourceUrl` and a null `verifiedOn`, and a test asserts that state. **AC-2 stays open** until a human compares each figure against the broker's published page; `ChargeScheduleService.findUnverified()` lists exactly those cards.
- **AC-6 stays open too.** The engine applies a holding-period predicate and `ChargeEngineTest` asserts it, but no seeded instrument profile carries an exit load, so nothing exercises it end to end.
- **No AMC rate card**, so `AmcChargeService` has nothing to bill against and `/charges/amc/impose` returns an empty list against the shipped data.
- **The resolver cache is evicted only by the publish path.** A rate card written straight to the repository is invisible to the engine until something evicts it.
- The old implementation is intact and still live, including `/broker-charges/amc/impose` — which is *not* the same endpoint as the new one, and running both against one period would charge twice. It is deleted in Phase C, not before.

## Review guide

`docs/charges-engine/README.md` §11 is the narrative and carries the Phase A gate item by item; `implementation-checklist.md` is the tracker, current through Chunk 9, with every open box saying why it is open. Acceptance criteria are signed off there with named evidence.

The two commits worth reading closely are the API surface and the integration tier — the first because it decides the shape callers will live with, the second because it is where the authorisation gaps surfaced.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
