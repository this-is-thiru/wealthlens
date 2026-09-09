Phase A of the charges engine plus Phase B's shadow recording: a standalone replacement for the broker-charges implementation, built alongside the existing one and now computing beside it under a flag that ships **off**.

Phase A's exit criterion held to the end — nothing under `portfolio/` changed while the engine was being built. Phase B then spends that isolation deliberately and minimally: `git diff master --stat -- backend/src/main/java/com/thiru/wealthlens/portfolio/` is one file and fifteen inserted lines, plus one new interface. Turning `app.charges.shadow-recording` off is the entire rollback.

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

## Phase B — shadow recording

The engine now sees every trade the live flow processes and changes none of it. `ProfitAndLossService` hands each V2 buy and sell to a `ChargeRecordingGateway` — an interface owned by `portfolio`, implemented in `brokercharges` — and **ignores what comes back**. No cost basis, no P&L figure and no stored transaction reads a computed charge. `GET /user-charges/user/{email}/reconciliation` is what the phase is for: computed against user-entered, per trade, with the delta.

Two decisions here are worth reading before touching this code, both recorded as **ADR-28**.

**The `assetType == EQUITY` gate stays.** The checklist and tech-spec §9.2 both said to remove it, citing FR-8. That instruction was wrong, and following it would have been a live behaviour change: `UserBrokerChargeService` resolves a rate card by **broker and date only**, with no asset-type dimension anywhere in it, so a mutual fund passed through the superseded implementation would be charged equity brokerage, STT and stamp duty — and `updateBrokerChargesReport` would write those figures into the P&L. Chunk 8's own gate forbids exactly that, so the checklist held two instructions that could not both be satisfied. The shadow call goes **outside** the gate instead: every asset type reaches the new engine, which does have that dimension, and the gate is removed in Phase C where the branch behind it is deleted anyway.

**Only the V2 flow is instrumented**, V1 `addTransaction` being unused and kept for version history. That maps exactly onto the two `updateProfitAndLoss` overloads, which are distinct methods rather than one path — the `ProfitLossContext` overload is instrumented, the `@Deprecated(forRemoval = true)` one reached only from V1 `sellStock` is untouched.

Three properties are asserted rather than asserted-about:

- **Off by default.** `ShadowRecordingIntegrationTest` is the only class running with the flag on, so every other integration class staying green is itself the evidence that recording is opt-in.
- **Nothing escapes into the trade path.** Every failure in the gateway is caught and logged with the transaction id. A trade must not fail to save because its shadow copy could not be priced; the missing row shows up as `transactionsWithoutComputation` in the reconciliation report.
- **The P&L is untouched.** The gateway returns a total of ₹999.99 and the saved document carries no trace of it — with the recording asserted present first, so the test is not vacuous.

The reconciliation report excludes two kinds of row from its totals and says why on each: a computation that did not resolve, and a row whose transaction is gone. Subtracting either produces a number that reads as a defect and is not one — the first as the engine undercharging by the whole entered amount, the second as it overcharging by its whole total.

**What is not done:** the flag has not been turned on against real data, and the deltas have not been reviewed. That is the last Phase B box and it needs an environment; `staging-runbook.md` §5b is the procedure, including the deltas that are expected and what each means.

## What is here

Seven calculators behind one strategy interface; an orchestrator applying aggregator → floor/cap → rounding once per line, in that order and never inside a calculator; two resolvers with specificity ranking and caching; a write-time validator; seven services; twelve catalogue codes, six seeded rate cards and two seeded scheme profiles; a code-keyed reporting model; four controllers and eleven documented requests in `api-collection/`.

`POST /charges/simulate` is the one worth calling out. It prices a trade and records nothing — structurally, not by promise: the service holds the engine and no repository, and a test reads the class's own fields and fails if that stops being true. It makes the whole engine exercisable without a portfolio to mutate, FIFO lots included, so a holding-period charge can be seen from the API rather than only from the suite.

**AC-6 is now closed.** Two scheme profiles are seeded — one exit load graded by holding period, one expressed as the predicate `#holdingDays < 7`, both priced per FIFO lot. A redemption drawn from lots of different ages charges the young ones alone; averaging over the transaction would be wrong by the entire charge rather than by a rounding error. The AMC card is seeded too, unscoped because the cycle context carries no scrip, quantity or asset type and a card declaring any of those is disqualified by the resolver.

**836 tests** across both tiers. **99% mutation score** (538/539) across the engine and the new services, the single survivor being a known equivalent mutant; both JaCoCo gates green.

Four test tiers do more than check examples:

- **Golden contract notes (16)** — whole trades priced end to end against the shipped cards, with expected figures computed by hand *before* the engine was run. Asserted line by line as well as in total, because a right total can hide two compensating errors. The zero-charge fixtures also assert *why* nothing was charged, so a free redemption cannot be confused with a scheme profile that failed to load — checked by hiding the profiles and watching them go red.
- **Invariants (10 properties × 200 generated cards)** — relationships that must hold whatever a card says. Verified non-vacuous by reintroducing D1 and confirming two properties fail.
- **The extensibility guarantee (3)** — every charge in `ChargeExtensibilityTest` is `SYNTHETIC_LEVY_FOR_TEST`, a code that exists in a catalogue row and a rate card and **nowhere in Java**. It is computed, recorded and aggregated; a `DERIVED` rule can name it in its base; repricing it applies only after the boundary. If anyone later reaches for a switch on charge code, these fail. That is the design promise stated in a way the build can defend.
- **Mutation testing** — which found a dead negative-zero guard, five unkillable mutants that were one untested warning, a zero-price boundary documented in a comment and asserted nowhere, and several fields written but never asserted. `testsupport/LogCapture` exists because a branch that only logs is otherwise indistinguishable from one that was deleted.

## Also in this branch

- **pitest was silently broken on Java 25** (`Unsupported class file major version 69`), so the mutation profile had never run on this toolchain. Bumped to 1.20.3.
- **Both quality gates widened** to cover `brokercharges.service`, judged per class. The superseded implementation is included rather than exempted — it was assumed it would fail the bar and it does not.

## Known and deliberate

- **Seeding does not update a card already on file.** `ChargeSeederService` is idempotent by `scheduleCode`, deliberately, so an operator's edits survive a restart — which also means the AC-2 corrections do not reach a database that already seeded the old cards. Remove the `_2025_04` documents and restart, or publish through the API. Safe now because nothing in the trade path prices anything; it stops being safe when Phase B starts recording.
- **Depository deduplication is not visible from `/charges/simulate`.** It checks recorded charges, and in Phase A nothing in the trade path records any, so a scoped charge always prices as a first occurrence. The trade path starts recording in Phase B.
- **The resolver cache is evicted only by the publish path.** A rate card written straight to the repository is invisible to the engine until something evicts it.
- The old implementation is intact and still live, including `/broker-charges/amc/impose` — which is *not* the same endpoint as the new one, and running both against one period would charge twice. It is deleted in Phase C, not before.

## AC-2 is closed, and finding out how cost more than expected

Rates shipped as marked placeholders (ADR-18) so the engine could be built without waiting on rate research. Verifying them against Zerodha's, Upstox's and Fyers' published pages turned up five defects and two rate changes:

| | |
|---|---|
| **Fyers brokerage** | 0.1% against a published 0.3% |
| **Upstox brokerage** | modelled as `MIN(0.1%, ₹20)`; it is a flat ₹20 with no percentage at all, so every trade below ₹20,000 was **undercharged** |
| **IPFT** | missing entirely from the Zerodha intraday, Upstox and Fyers cards, and from their GST bases |
| **Upstox depository fee** | ₹18.50 against ₹20.00 |
| **NSE transaction charge** | raised 0.00297% → 0.0030699% on **2026-03-01**, inside the shipped cards' window |
| **Zerodha depository fee** | cut from ₹13.50 to ₹13.00, mid-2026, likewise inside the window |

**Both brokerage errors were invisible at ₹1,00,000**, because a ₹20 cap binds whatever the percentage says — and ₹1,00,000 is the trade size every golden fixture used. A fixture at one trade size tests one trade size. Small-trade fixtures now exist for both.

The two rate changes are the more interesting half: they landed *inside* the cards' validity window, so the correction was not to edit the cards but to publish successors. Eleven cards now ship in three generations, and the same ₹1,00,000 Zerodha sell prices at **₹119.67**, **₹119.79** and **₹119.20** depending on its date. That is ADR-12's temporal model doing the job it was designed for, on its first contact with a real rate change — and replacing every rate touched **JSON only**, which is the extensibility claim demonstrated rather than asserted.

Evidence, with every figure sourced: `docs/charges-engine/ac2-rate-verification.md`.

## Making rate cards deployable (ADR-26)

Raised while reviewing the AC-2 work, and the sharpest question asked of this design: *what happens when this deploys to production?*

The seeder is idempotent by `scheduleCode`, so a card already on file is never overwritten — correct, because an operator's correction must survive a restart, but it means an edited seed file simply never arrives, silently and for ever. Applying the AC-2 corrections needed the documents deleted by hand, which is not a deployment mechanism.

Nothing needs to be. `scheduleCode` carries a generation, so **a rate change is always a new code** and the seeder applies it on the next deploy with no manual step. The mechanism already worked; the missing piece was the rule that keeps it working — *a deployed card is never edited, only superseded* — plus two guards:

- **Drift is reported.** The seeder warns when a card on file differs from its shipped file, and `GET /charge-schedules/drift` lists the differences. It deliberately does not say which side is right: a rate corrected in production and a file nobody deployed look identical and need opposite fixes.
- **Verification expires.** `findUnverified()` now ages out a `verifiedOn` after 90 days. Without it AC-2 closes once and the worklist stays empty while reality moves — which is exactly what happened between April 2025 and September 2026, under cards that had been signed off.

**Seeding also stopped happening at startup (ADR-27).** `POST /charges/seed`, `SUPER_USER`, is now the only way shipped data enters a database — so no deployment writes rate cards as a side effect of booting, and every seeded document records who asked and when. Building that turned up a defect worth more than the feature: seeded cards carried **no audit metadata at all**, while an ordinary transaction records who wrote it. The cause is not the auditing, which works — it is that Lombok stamps `@ConstructorProperties` on `@AllArgsConstructor`, Jackson honours it as a creator, and the field initialiser never runs, so the entity reaches Spring Data with a null `AuditMetadata` for it to fill. Parsing into a pre-built instance is the entire fix; no audit field is assigned anywhere. **Any entity this application deserialises from JSON has the same silent hole.**

`POST /charges/recompute`, the third piece, is designed in tech-spec §14.4 and belongs with Phase C, where something finally records charges for it to recompute.

## Review guide

`docs/charges-engine/README.md` §11 is the narrative and carries the Phase A gate item by item; `implementation-checklist.md` is the tracker, current through Chunk 9, with every open box saying why it is open. Acceptance criteria are signed off there with named evidence.

The two commits worth reading closely are the API surface and the integration tier — the first because it decides the shape callers will live with, the second because it is where the authorisation gaps surfaced.

One more worth a glance: the scheme-profile commit widened `ChargeScheduleValidator` to cover instrument profiles. Validating only the broker's card had left exit load — the one charge a rate card cannot express — entirely unchecked, and it is the rule most worth checking, because a mistake in something conditioned on a holding period is invisible on every redemption the condition excludes.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
