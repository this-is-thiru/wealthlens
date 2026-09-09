# Phase B — the reconciliation review

**What this is:** the Phase B exit artifact. The gate says "reconciliation deltas reviewed and
explained"; this is that review, run on 2026-09-09 against the `it-staging` database with 319 real
transactions spanning 2023-06-22 to 2026-01-12.

**The headline, stated first because it changes what the gate can mean:** the user-entered
`brokerCharges` field was never meaningfully populated, so it cannot serve as the baseline the gate
assumes. The engine was therefore verified a different way — by hand, against the shipped card, line
by line — and it is correct.

---

## 1. What was run

```
POST /charges/backfill/user/{email}          # 319 transactions priced
GET  /user-charges/user/{email}/reconciliation
```

Shadow recording alone would have produced nothing: all 319 transactions predate the flag, so every
one had no computed charge and the report would have returned `rows: []`. The backfill is what made
the review possible at all.

## 2. The numbers

| Backfill | | Reconciliation | |
|---|---|---|---|
| `transactionsRead` | 319 | `totalComputed` | ₹240.62 |
| `priced` | 319 | `totalEntered` | **₹5.32** |
| `skipped` | 0 | `totalDelta` | ₹235.30 |
| `sellsWithNoLotsFound` | **0** | `comparableCount` | 49 |
| `totalComputed` | ₹242.62 | `unresolvedCount` | 270 |

```
byResolution: RESOLVED 45 | NO_SCHEDULE 227 | NO_INSTRUMENT_PROFILE 43 | CORPORATE_ACTION_EXEMPT 4
```

The ₹2.00 between the two computed totals is the 43 `NO_INSTRUMENT_PROFILE` rows, which carry broker
charges but are excluded from the reconciliation totals because part of their computation is missing.

## 3. The finding: there is no baseline to reconcile against

₹5.32 of entered charges across 49 comparable trades, and ₹40.72 across all 319. **37 of the 49
comparable rows have an entered value of exactly ₹0.01.** The largest is ₹3.40.

A ₹1,00,000 Zerodha delivery sell costs about ₹120. These are not charges; they are a field that was
filled in with a placeholder or left alone.

| | computed | entered |
|---|---|---|
| DEEPAKNTR SELL 2025-12-08 (Fyers) | 28.76 | 0.92 |
| BIRET-RR SELL 2025-05-05 (Fyers) | 28.19 | 0.88 |
| EMAMILTD SELL 2025-05-05 (Fyers) | 26.82 | 0.76 |

**So `totalDelta` of ₹235.30 is not a discrepancy between two opinions about the same charge. It is
very nearly the whole computed total, measured against a field nobody filled in.**

That is not a disappointing result. It is the clearest possible statement of the problem the engine
was built for: charges were supposed to be entered by hand, and they were not. PRD OD-8 asked for
this comparison as proof before cutover; what it actually proves is that the manual figure was never
a source of truth to preserve.

**Consequence for Phase C.** The plan (checklist Chunk 10) removes `brokerCharges` from
`AssetRequest` and lets the computed total drive cost basis. Nothing of value is lost: on this
database the field holds ₹40.72 in total. But it also means **cost basis is about to change for every
trade**, from ~0 charges to real ones, and that is a user-visible move in realised P&L that should be
announced rather than discovered.

## 4. How the engine was verified instead

Since the deltas cannot validate it, three independent checks were used.

**A prediction made before running, and matched exactly.** From the transaction dates and the shipped
cards' windows: 227 transactions fall before the earliest card (2025-04-01) and must resolve
`NO_SCHEDULE`; 92 fall inside. The engine returned 227 and 92 (45 + 43 + 4). The `NO_SCHEDULE` rows
range 2023-06-22 to 2025-03-27 — not one of them is inside a card's window.

**One contract note checked by hand.** DEEPAKNTR SELL, Fyers, turnover ₹3,079.60:

| Line | Expected | Engine |
|---|---|---|
| BROKERAGE 0.3%, cap ₹20 | 9.2388 → 9.24 | 9.24 |
| STT 0.1%, `HALF_UP_0` | 3.0796 → **3** | 3.00 |
| EXCHANGE_TXN 0.00297% | 0.0915 → 0.09 | 0.09 |
| SEBI_FEE 0.0001% | 0.0031 → 0.00 | 0.00 |
| IPFT 0.0001% | 0.0031 → 0.00 | 0.00 |
| DP flat | 12.50 | 12.50 |
| GST 18% of (9.24+0.09+0+0+12.50) = 21.83 | 3.9294 → 3.93 | 3.93 |
| **Total** | **28.76** | **28.76** |

Every line matches. Two things are worth pointing at: STT rounds to the whole rupee, which is the
statutory treatment and is why it reads 3.00 and not 3.08; and the GST base is ₹21.83, which
**excludes STT** — the D1 defect, fixed, visible on a real trade rather than in a fixture.

**AC-4 proven on real data.** EMAMILTD was sold three times on 2025-05-05 through Fyers. The
depository charge appears once:

```
txn ...60bf   DP 12.50
txn ...60c2   DP  0.00
txn ...60c4   DP  0.00
```

The old implementation's D9 and D10 defects were both in this deduplication. This is the first time
the replacement has been shown to get it right on documents nobody wrote for a test.

## 5. Gaps found in the shipped data

**227 trades have no rate card (71% of the history).** Every card starts 2025-04-01, and this user's
history starts 2023-06-22. They are recorded as `NO_SCHEDULE` and are visible in the gaps report
rather than silently priced at zero — which is the designed behaviour — but backfilling a portfolio's
real history means most of it cannot be priced at all. **Closing this needs 2023 and 2024 generations
of each card**, which is rate archaeology, not code.

**43 mutual-fund buys have no instrument profile**, across 8 schemes, none of them the two shipped
(`HDFCLIQUID`, `PARAGPARIKHFLEXICAP`). This cost nothing here: *every one of the 43 is a BUY*, and
exit load is a redemption charge. The exposure is latent, not realised.

**A key-shape mismatch worth knowing before writing more profiles.** Real mutual-fund holdings carry
`stockCode` values like `EDELWEISS NIFTY SMALLCAP 250 INDEX FUND - DIRECT PLAN` — the full scheme
name. The shipped profiles are keyed on short codes. `ChargeInstrumentEntity` is keyed on
`stockCode` (README §8.14), so a profile must use the exact string the portfolio stores, not a tidy
code, or it will never resolve.

**No account holder.** All 319 rows have `accountHolder: null`. Deduplication keys include it
(ADR-25, D10), so it is consistent here — but the D10 defect it was written for cannot occur on this
data, and would begin to matter the moment a second holder appears.

## 6. What this closes, and what it does not

**Closed.** The mechanism works end to end on real data: 319 transactions priced, FIFO lots
reconstructed for all 43 sells with none missing, deduplication correct, arithmetic correct to the
paisa, and every resolution accounted for against a prediction made in advance.

**Not closed, and cannot be on this data.** "Computed vs user-entered deltas explained" in the sense
the gate intended — two independent opinions about the same charge, reconciled. The entered side does
not exist. Either a database with genuinely populated broker charges is found, or **the gate should
be amended to record that the comparison was attempted and the baseline was absent**, which is a
finding rather than a failure.

Recommendation: amend it. Waiting for data that was never captured would block Phase C indefinitely,
and the engine has been verified by stronger means than a comparison against ₹0.01.
