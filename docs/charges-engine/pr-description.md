Phase A of the charges engine: a standalone replacement for the broker-charges implementation, built alongside the existing one and wired to nothing. **No production code calls it yet**, and `portfolio/` is untouched — `git diff master --stat -- backend/src/main/java/com/thiru/wealthlens/portfolio/` is empty, which is Phase A's exit criterion and what keeps the eventual cutover reversible.

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

## What is here

Seven calculators behind one strategy interface; an orchestrator applying aggregator → floor/cap → rounding once per line, in that order and never inside a calculator; two resolvers with specificity ranking and caching; a write-time validator; five services; twelve catalogue codes and five seeded rate cards.

**684 tests.** Every charges class at 100% mutation coverage bar one equivalent mutant; both JaCoCo gates green.

Three test tiers do more than check examples:

- **Golden contract notes (12)** — whole trades priced end to end against the shipped cards, with expected figures computed by hand *before* the engine was run. Asserted line by line as well as in total, because a right total can hide two compensating errors.
- **Invariants (10 properties × 200 generated cards)** — relationships that must hold whatever a card says. Verified non-vacuous by reintroducing D1 and confirming two properties fail.
- **Mutation testing** — which found a dead negative-zero guard, five unkillable mutants that were one untested warning, and several fields written but never asserted. `testsupport/LogCapture` exists because a branch that only logs is otherwise indistinguishable from one that was deleted.

## Also in this branch

- **pitest was silently broken on Java 25** (`Unsupported class file major version 69`), so the mutation profile had never run on this toolchain. Bumped to 1.20.3.
- **Both quality gates widened** to cover `brokercharges.service`, judged per class. The superseded implementation is included rather than exempted — it was assumed it would fail the bar and it does not.

## Known and deliberate

- **Rates are placeholders.** Every card carries `sourceUrl` and a null `verifiedOn`, and a test asserts that state. **AC-2 stays open** until a human compares each figure against the broker's published page; `ChargeScheduleService.findUnverified()` lists exactly those cards.
- **No AMC rate card**, so `AmcChargeService` has nothing to bill against yet.
- The old implementation is intact and still live. It is deleted in Phase C, not before.

## Review guide

`docs/charges-engine/README.md` §11 is the narrative; `implementation-checklist.md` is the tracker, current through Chunk 6, with four boxes deliberately open and each saying why. Acceptance criteria are signed off there with named evidence.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
