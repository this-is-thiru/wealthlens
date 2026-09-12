# Trade ledger — a realised-trade record you can file taxes from

**Branch:** `feature/trade-ledger` → `feature/charges-engine-core` · 11 commits · 70 files

**Stacked.** Review the charges engine first; this targets that branch, so the diff shown is only
the trade-ledger work.

The charges engine computes what a trade costs. This makes that cost *usable* — on the realised-trade
record, classified the way tax actually turns on it.

## Read in this order

1. `docs/trade-ledger/implementation-checklist.md` — decisions D1–D8, which are settled; do not
   re-litigate them in review, argue with the decision
2. `docs/trade-ledger/holding-period-analysis.md` — the tax rules, with confidence stated per rule
3. `docs/trade-ledger/migration.md` — **two mandatory data steps before deploying**
4. `docs/trade-ledger/backlog.md` — found, real, deliberately not done

## What it fixes

`TradeOutcomeEntity` could not express what tax turns on: which charges are deductible, which
holding-period rule applies, or whether the trade is capital gains at all. And **the V2 sell — the
live path — wrote no trade-outcome row whatsoever**, so a V2 user had aggregate totals and nothing
itemised to file from.

- **TL-4** holding-period rules are data-driven and validity-windowed. Four hardcoded copies of a
  365-day rule existed and did not even agree — two compared `plusYears(1)`, two counted 365 days
- **TL-5** each charge code declares `deductibleForCapitalGains`. Three of twelve are not: STT
  (expressly disallowed, and the largest charge on a delivery sell), AMC and account opening
- **TL-6** `TradeClassifier`, `TradeOutcomeRecorder`, per-code charge breakups, and
  `gainsByClassification` beside the legacy pair
- **TL-7** idempotency, three routes in order of authority

## Two defects found by this work, not by the ticket

**The profit-and-loss endpoint returned `0.0` for every amount.** `ReportModelResponse` said
`purchasePrice`/`sellPrice`/`brokerCharges` where the entity says
`purchaseAmount`/`sellAmount`/`brokerage`, and `safeCopy` maps by name. Only `profit` and
`miscCharges` ever came through — a profit with no turnover behind it. **This renames three response
keys.**

**A replayed trade was applied to the portfolio twice.** `addTransaction` deduped the transaction
row, logged "Duplicate transaction suppressed", and the caller applied the trade anyway. The logging
concealed it. Fixed by making the outcome say what happened rather than returning an id alone.

## V1 is untouched, and it is checked

`buyStock`, `sellStock`, `buyStockV2`, `sellStockV2` are byte-identical. The TL-6 change to
`PortfolioService` is nine added lines and no deleted ones. A test drives V1's real `sellStock` and
asserts no interaction with the new recorder — because if V1 ever reached it, every V1 sell would be
recorded twice.

The one exception, agreed explicitly: `processTransaction` gained a replay guard. It returns
*before* `buyStock`/`sellStock`, so nothing inside them moved.

## What reviewers should push on

- **D7** — the instrument sub-class comes from the epic's registry, not `ChargeInstrumentEntity`.
  Consequence: every mutual fund and bond disposal records `UNCLASSIFIED` until that ships
- **D8** — a map beside the two named fields rather than replacing them. Do not sum that map
- **The 60-second idempotency window** — two genuinely identical trades inside it are collapsed. The
  escape hatch is a client-supplied key
- **B-1 in the backlog** — `TradeOutcomeRecorder` is at 62% branch coverage and is outside both
  JaCoCo gates. It assembles the row a tax return is filed from

## Before merging

`docs/trade-ledger/migration.md` §1 and §2 are **mandatory**. Without §1, every trade for an existing
user in an existing financial year fails with `DuplicateKeyException`.

## Gates

907 tests across both tiers, both JaCoCo gates passing, surefire XML gate clean, spotless clean.
