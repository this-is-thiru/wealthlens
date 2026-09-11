# Charges Engine — staging runbook

Every request the charges engine answers, in the order they make sense to run, with the numbers each
one should return. It began as the AC-2 rate-verification script; with AC-2 closed it is now the
fastest way to confirm a deployment came up with its seed data intact, and §5b is where Phase B's
shadow recording gets exercised.

**Expected figures come from the golden fixtures**, so a mismatch means the deployment is not running
this branch — or is running an environment seeded before 2026-09-08, which §7b fixes.

> **The rates were verified on 2026-09-08** against Zerodha's, Upstox's and Fyers' published pages;
> the evidence is in `ac2-rate-verification.md`. All eleven cards carry a `verifiedOn`, so
> `GET /charge-schedules/unverified` returns `[]` on a freshly seeded database. §7 is how you correct
> a rate when one changes — by superseding the card, never by editing it (ADR-26).

---

## 0. Set up

```bash
export BASE=http://localhost:8080          # or the staging host
```

Responses are wrapped: every body is `{"data": ...}`. The `jq` paths below account for that.

---

## 1. Create a super user

Publishing a rate card and running the AMC cycle both require `SUPER_USER`. Everything else needs
only a logged-in user.

```bash
curl -sS -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{
        "email": "charges-admin@wealthlens.test",
        "password": "Charges@123",
        "role": "SUPER_USER"
      }'
```

> **Note, and it is not a small one:** `AuthService.addUser` writes `request.getRole()` verbatim, so
> anyone who can reach `/auth/register` can register themselves as `SUPER_USER`. That is what makes
> this step possible at all. It is fine in staging and is worth raising separately for production —
> the `SUPER_USER` guard on rate-card publishing is only as strong as registration is.

## 2. Log in and keep the token

```bash
export TOKEN=$(curl -sS -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"charges-admin@wealthlens.test","password":"Charges@123"}' \
  | jq -r '.data.access_token')

echo "${TOKEN:0:24}…"        # non-empty means you are in
```

Tokens expire in 30 minutes. Re-run this when a call starts returning 401/403.

---

## 2a. Apply the seed data — required after every deploy to a fresh database

Nothing seeds at startup (ADR-27). A database that has never been seeded has no rate cards, and
every trade prices as `NO_SCHEDULE` until this is run.

```bash
curl -sS -X POST "$BASE/charges/seed" -H "Authorization: Bearer $TOKEN" \
  | jq '.data | {seededBy, catalogueCreated: (.catalogueCreated|length),
                 schedulesCreated, instrumentsCreated, schedulesSkipped, drift: (.drift|length)}'
```

Expect on a fresh database: 12 catalogue codes, 11 rate cards, 2 scheme profiles, nothing skipped,
no drift. Every document it writes records `seededBy` and the time — which is the reason this is an
endpoint and not a startup hook.

**Idempotent.** Run it again and `schedulesCreated` is empty. Safe on a deployment checklist.

Requires `SUPER_USER`: it writes the rate cards every user is charged against.

## 3. Confirm the deployment has its seed data

### 3.1 The charge catalogue — 12 codes

```bash
curl -sS "$BASE/charge-catalogue" -H "Authorization: Bearer $TOKEN" | jq '.data | length, [.[].code]'
```

Expect `12` and:
`BROKERAGE, STT, EXCHANGE_TXN, SEBI_FEE, IPFT, STAMP_DUTY, DP, GST, AMC, ACCOUNT_OPENING, EXIT_LOAD, MF_TXN_FEE`

### 3.2 The AC-2 worklist — every card whose rates nobody has checked

```bash
curl -sS "$BASE/charge-schedules/unverified" -H "Authorization: Bearer $TOKEN" \
  | jq '.data | map({scheduleCode, brokerName, assetType, segment, startDate, sourceUrl, verifiedOn})'
```

Expect **`[]`**. Every shipped card was verified on 2026-09-08 and carries a `verifiedOn` date, which
is what closed AC-2 (see `ac2-rate-verification.md`).

A card appearing here means someone published one without checking its rates, or an existing one lost
its date. It is a worklist, not a historical record — re-run it after every publish.

Eleven cards ship, in three generations where a rate changed:

| scheduleCode | scope | window |
|---|---|---|
| `ZERODHA_EQ_DELIVERY_2025_04` | EQUITY / DELIVERY | 2025-04-01 → 2026-02-28 |
| `ZERODHA_EQ_DELIVERY_2026_03` | " | 2026-03-01 → 2026-06-18 |
| `ZERODHA_EQ_DELIVERY_2026_06` | " | 2026-06-19 → open |
| `ZERODHA_EQ_INTRADAY_2025_04` / `_2026_03` | EQUITY / INTRADAY | split at 2026-03-01 |
| `UPSTOX_EQ_DELIVERY_2025_04` / `_2026_03` | EQUITY / DELIVERY | split at 2026-03-01 |
| `FYERS_EQ_DELIVERY_2025_04` / `_2026_03` | EQUITY / DELIVERY | split at 2026-03-01 |
| `ZERODHA_MF_2025_04` | MUTUAL_FUND | 2025-04-01 → open |
| `ZERODHA_MAINTENANCE_2025_04` | unscoped | 2025-04-01 → open |

### 3.3 One broker's cards

```bash
curl -sS "$BASE/charge-schedules?broker=ZERODHA" -H "Authorization: Bearer $TOKEN" \
  | jq '.data | map({scheduleCode, assetType, segment, startDate, endDate, status})'
```

### 3.4 One card in full — this is what you check the rates against

```bash
curl -sS "$BASE/charge-schedules/ZERODHA_EQ_DELIVERY_2025_04" -H "Authorization: Bearer $TOKEN" \
  | jq '.data.rules | map({code, basis, rate, flatAmount, side, events, baseCodes, rounding})'
```

---

## 4. Price trades — `POST /charges/simulate`

Records nothing. Safe to run against staging as often as you like.

### 4.1 Zerodha equity delivery, ₹1,00,000 sell

```bash
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "brokerName": "ZERODHA",
        "assetType": "EQUITY",
        "segment": "DELIVERY",
        "exchange": "NSE",
        "event": "SELL",
        "stockCode": "INFY",
        "price": 1000,
        "quantity": 100,
        "transactionDate": "2025-06-02"
      }' | jq '.data | {scheduleCode, resolution, amountByCode, totalCharges}'
```

Expect: `BROKERAGE 0.00, STT 100.00, EXCHANGE_TXN 2.97, SEBI_FEE 0.10, IPFT 0.10, DP 13.50, GST 3.00`
— **total ₹119.67**, `resolution: RESOLVED`.

GST is 18% of brokerage + exchange + SEBI + IPFT + DP only. **If STT or stamp duty ever appears in
that base, that is defect D1 back again.**

### 4.2 The same trade as a buy — stamp duty appears, DP does not

```bash
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "brokerName": "ZERODHA", "assetType": "EQUITY", "segment": "DELIVERY", "exchange": "NSE",
        "event": "BUY", "stockCode": "INFY", "price": 1000, "quantity": 100,
        "transactionDate": "2025-06-02"
      }' | jq '.data | {resolution, amountByCode, totalCharges}'
```

Expect: `BROKERAGE 0.00, STT 100.00, EXCHANGE_TXN 2.97, SEBI_FEE 0.10, IPFT 0.10, STAMP_DUTY 15.00,
GST 0.57` — **total ₹118.74**.

### 4.3 Intraday — a different card, chosen by `segment`

```bash
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "brokerName": "ZERODHA", "assetType": "EQUITY", "segment": "INTRADAY", "exchange": "NSE",
        "event": "SELL", "stockCode": "INFY", "price": 1000, "quantity": 100,
        "transactionDate": "2025-06-02"
      }' | jq '.data | {scheduleCode, amountByCode, totalCharges}'
```

Expect `scheduleCode: ZERODHA_EQ_INTRADAY_2025_04`, `BROKERAGE 20.00, STT 25.00, EXCHANGE_TXN 2.97,
SEBI_FEE 0.10, IPFT 0.10, GST 4.17` — **total ₹52.34**. No DP, no stamp duty on a sell.

### 4.4 The brokerage cap — same card, three trade sizes

```bash
for QTY in 10 100 5000; do
  echo "--- quantity $QTY"
  curl -sS -X POST "$BASE/charges/simulate" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"brokerName\":\"ZERODHA\",\"assetType\":\"EQUITY\",\"segment\":\"INTRADAY\",
         \"exchange\":\"NSE\",\"event\":\"SELL\",\"stockCode\":\"INFY\",\"price\":1000,
         \"quantity\":$QTY,\"transactionDate\":\"2025-06-02\"}" \
    | jq -c '.data | {BROKERAGE: .amountByCode.BROKERAGE, total: .totalCharges}'
done
```

Expect brokerage `3.00`, `20.00`, `20.00` — the percentage rate below the cap, then the cap twice.
Totals `6.92`, `52.34`, `1460.63`.

Worth running for a second reason: **the cap is where two shipped brokerage rates hid**. Fyers was
0.1% against a published 0.3%, and Upstox carried a percentage it does not have — both invisible at
₹1,00,000 because the ₹20 cap binds either way. Small trades are the only size that tests a rate.

### 4.5 Mutual fund, equity-oriented — a charge the old design could not express

```bash
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "brokerName": "ZERODHA", "assetType": "MUTUAL_FUND",
        "event": "SELL", "stockCode": "PARAGPARIKHFLEXICAP",
        "price": 100, "quantity": 1000, "transactionDate": "2025-06-02"
      }' | jq '.data | {scheduleCode, instrumentId, resolution, amountByCode, totalCharges}'
```

Expect `STT 1.00`, total **₹1.00**, `resolution: RESOLVED`, and a non-null `instrumentId`. The tax
applies only because the scheme profile says the fund is equity-oriented — no rate card knows that.

### 4.6 The same call against the liquid fund — the attribute flips one charge off

```bash
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "brokerName": "ZERODHA", "assetType": "MUTUAL_FUND",
        "event": "SELL", "stockCode": "HDFCLIQUID",
        "price": 100, "quantity": 1000, "transactionDate": "2025-06-02"
      }' | jq '.data | {resolution, amountByCode, totalCharges}'
```

Expect **no STT at all** and `resolution: NO_MATCHING_RULES` — the scheme is not equity-oriented, so
the only rule on the card drops out. Read that as "priced, and genuinely nothing applied", which is a
different fact from `NO_INSTRUMENT_PROFILE`.

### 4.7 A scheme with no profile on file — the gap, made visible

```bash
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "brokerName": "ZERODHA", "assetType": "MUTUAL_FUND",
        "event": "SELL", "stockCode": "SOME_FUND_WE_HAVE_NOT_SEEDED",
        "price": 100, "quantity": 1000, "transactionDate": "2025-06-02"
      }' | jq '.data | {resolution, instrumentId, totalCharges}'
```

Expect `resolution: NO_INSTRUMENT_PROFILE`, `instrumentId: null`, total `0.0`. Only two schemes have
profiles; every other fund lands here by design (ADR-24) rather than being blocked.

### 4.7b The same trade after a rate change

```bash
for DATE in 2025-06-02 2026-04-01 2026-07-01; do
  echo "--- $DATE"
  curl -sS -X POST "$BASE/charges/simulate" \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -d "{\"brokerName\":\"ZERODHA\",\"assetType\":\"EQUITY\",\"segment\":\"DELIVERY\",
         \"exchange\":\"NSE\",\"event\":\"SELL\",\"stockCode\":\"INFY\",\"price\":1000,
         \"quantity\":100,\"transactionDate\":\"$DATE\"}" \
    | jq -c '.data | {schedule: .scheduleCode, EXCHANGE_TXN: .amountByCode.EXCHANGE_TXN, DP: .amountByCode.DP, total: .totalCharges}'
done
```

Expect three different cards and three different totals — **₹119.67**, **₹119.79**, **₹119.20**. NSE
raised the transaction charge on 2026-03-01 and Zerodha cut its depository fee on 2026-06-19, and a
backfilled 2025 trade still prices at 2025 rates. This is the temporal model earning its place.

### 4.8 Exit load — a redemption drawn from two lots of different ages

This is AC-6 over HTTP. Exit load is priced **per FIFO lot**, so the request has to say which lots
the redemption drew on; without `lots` a `perLot` rule evaluates zero times and answers ₹0 however
the profile is written.

```bash
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "brokerName": "ZERODHA", "assetType": "MUTUAL_FUND",
        "event": "SELL", "stockCode": "PARAGPARIKHFLEXICAP",
        "price": 100, "quantity": 1000, "transactionDate": "2025-06-02",
        "lots": [
          { "quantity": 600, "acquisitionDate": "2024-02-01", "price": 100 },
          { "quantity": 400, "acquisitionDate": "2025-03-01", "price": 100 }
        ]
      }' | jq '.data | {resolution, amountByCode, totalCharges}'
```

Expect `EXIT_LOAD 400.00, STT 1.00` — **total ₹401.00**. That is 1% of the *younger* lot's ₹40,000.
The 600 units held sixteen months attract nothing, and averaging the load over the whole redemption
would have charged ₹1,000.

Move the second lot's `acquisitionDate` back beyond a year and the exit load disappears entirely,
leaving `STT 1.00`. That is the predicate, visible.

### 4.9 A lot set that does not describe its disposal is refused

```bash
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "brokerName": "ZERODHA", "assetType": "MUTUAL_FUND",
        "event": "SELL", "stockCode": "PARAGPARIKHFLEXICAP",
        "price": 100, "quantity": 1000, "transactionDate": "2025-06-02",
        "lots": [ { "quantity": 900, "acquisitionDate": "2025-03-01", "price": 100 } ]
      }' -o /dev/null -w '%{http_code}\n'
```

Expect **400**. Priced as sent, the unaccounted 100 units would cost nothing and you would get a
smaller exit load rather than an error — which is the one failure an endpoint answering "what will
this cost?" must not produce. A lot acquired after the trade, or one with no `acquisitionDate`, is
refused for the same reason.

### ⚠️ What `/charges/simulate` still cannot show you

**Depository-charge deduplication.** Simulate checks *recorded* charges, and in Phase A nothing in
the trade path records any, so DP always prices as a first occurrence unless the account already
carries a row from the AMC cycle. That is Phase A's shape, not a defect — the trade path starts
recording in Phase B.

---

## 5. Account-level charges — the AMC cycle

### 5.1 Register a demat account

```bash
export USER_EMAIL=charges-admin@wealthlens.test

curl -sS -X POST "$BASE/charge-accounts/user/$USER_EMAIL" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "accountHolder": "self",
        "brokerName": "ZERODHA",
        "dematAccountId": "1208160000000001",
        "openedOn": "2025-04-01",
        "amcFrequency": "ANNUALLY"
      }' | jq '.data | {id, dematAccountId, amcFrequency, lastBilledThrough, status}'
```

Expect `status: ACTIVE`, `lastBilledThrough: null`.

### 5.2 List the accounts

```bash
curl -sS "$BASE/charge-accounts/user/$USER_EMAIL" -H "Authorization: Bearer $TOKEN" \
  | jq '.data | map({dematAccountId, amcFrequency, lastBilledThrough, billingEvents})'
```

### 5.3 Run the cycle — `SUPER_USER`, and it writes real charges

```bash
curl -sS -X POST "$BASE/charges/amc/impose?frequency=ANNUALLY&billedThrough=2025-12-31" \
  -H "Authorization: Bearer $TOKEN" \
  | jq '.data | map({dematAccountId, lastBilledThrough, billingEvents})'
```

Expect one account back, `lastBilledThrough: "2025-12-31"`, and one billing event of **₹354.00**
(AMC ₹300 + GST ₹54). This bills **every** account on that frequency across **every** user — not
just yours.

### 5.4 Run it again — nothing should happen

```bash
curl -sS -X POST "$BASE/charges/amc/impose?frequency=ANNUALLY&billedThrough=2025-12-31" \
  -H "Authorization: Bearer $TOKEN" | jq '.data | length'
```

Expect `0`. The watermark is what makes a retried job a no-op rather than a second charge.

### 5.5 Read the charge it wrote

```bash
curl -sS "$BASE/user-charges/user/$USER_EMAIL" -H "Authorization: Bearer $TOKEN" \
  | jq '.data | map({transactionId, event, scheduleCode, amountByCode, totalCharges, resolution})'
```

Expect one row, `transactionId: "AMC-1208160000000001-2025-12-31"`, `event: AMC_CYCLE`,
`{"AMC": 300.0, "GST": 54.0}`, total `354.0`.

### 5.6 Its contract note, line by line

```bash
curl -sS "$BASE/user-charges/user/$USER_EMAIL/transaction/AMC-1208160000000001-2025-12-31" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.lines'
```

### 5.7 Anything that could not be fully priced

```bash
curl -sS "$BASE/user-charges/user/$USER_EMAIL/gaps" -H "Authorization: Bearer $TOKEN" \
  | jq '.data | map({transactionId, resolution, transactionDate})'
```

Expect `[]` after a clean run. Rows here mean no card for the period, or no scheme profile.

### 5.8 History, narrowed

```bash
curl -sS "$BASE/user-charges/user/$USER_EMAIL?from=2025-01-01&to=2025-12-31" \
  -H "Authorization: Bearer $TOKEN" | jq '.data | length'
```

Both bounds or neither — a half-open range is rejected rather than guessed at.

---

## 5b. Phase B — shadow recording and the reconciliation report

This is the last open box in Phase B, and it is the reason the phase exists. Everything above prices
trades you asked it to price; this watches the engine price the trades the application was already
processing anyway, and compares its answer to what the user typed.

### 5b.1 Turn it on

Shadow recording is **off** in all three profile yamls. Turn it on for the environment only:

```bash
# as an environment variable on the running service
APP_CHARGES_SHADOW_RECORDING=true
```

or in the profile's yaml:

```yaml
app:
  charges:
    shadow-recording: true
```

Restart, then confirm the flag took by driving one trade and looking for its row (5b.3). There is no
endpoint that reports the flag's value — if the row does not appear, the flag did not take.

**What turning it on changes:** `ProfitAndLossService` hands every V2 buy and sell to the engine,
which prices it and writes a `user_charges` row. **Nothing else.** No cost basis, no P&L figure and
no stored transaction reads the computed number. Turning the flag back off is the entire rollback —
the rows already written stay, and remain readable through the endpoints below.

### 5b.2 Drive real trades

Use the ordinary transaction API — `POST /portfolio/user/{email}/transaction/v2`. Note the prefix:
it is `/portfolio/`, not `/transactions/` — the latter is `TransactionController`, which only reads.
The **v2** path is the one instrumented; V1 `POST .../transaction` is **in live use** and
deliberately untouched (ADR-28).

```bash
curl -sS -X POST "$BASE/portfolio/user/$USER_EMAIL/transaction/v2" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{
    "stockCode":"RELIANCE","stockName":"Reliance Industries","exchangeName":"NSE",
    "brokerName":"ZERODHA","assetType":"EQUITY","transactionType":"BUY",
    "accountType":"SELF","accountHolder":"self",
    "quantity":100,"price":1000,"brokerCharges":125.50,"miscCharges":0,
    "transactionDate":"2025-06-10","orderExecutionTime":"2025-06-10T10:15:00"
  }'
```

Include at least one **non-equity** trade. The superseded implementation skips those entirely, so
they are the trades where the engine is doing something nothing else does — and where the shipped
seed data is most likely to have a gap.

### 5b.2b Backfill the history — the step that makes the report mean anything

**On any database that already has transactions, do this before reading the report.** Shadow
recording only fires on trades that flow through the live path *after* the flag went on. Everything
older carries no computed charge, so without this step §5b.4 returns `rows: []` and
`transactionsWithoutComputation` equal to the user's entire history — an empty report that looks
like a broken one.

It is also the only way to get a delta against a figure a **user** actually typed. Trades driven by
hand in §5b.2 carry entered figures that were invented for the test; the history does not.

```bash
curl -sS -X POST "$BASE/charges/backfill/user/$USER_EMAIL" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
```

`SUPER_USER` only. **It writes** — one `user_charges` row per priced transaction, and nothing else:
no cost basis, no P&L figure and no transaction document is touched. Reversible by dropping the rows
it wrote. Safe to re-run: a row is keyed on `{email, transactionId}` and replaced, not appended.

Read the response in this order:

| Field | What to do about it |
|---|---|
| `skipped` | Transactions never processed (`TEMPORARY`, `FAILED`) or missing a date, quantity or side. A temporary transaction is blocked by a corporate action and has not happened yet |
| `sellsWithNoLotsFound` | Sells whose buys are not in the history. Any holding-period charge on those prices at zero, so this **caps how far the exit-load figures can be trusted**. Expect a non-zero count on a database whose earliest buys predate its transaction records |
| `byResolution` | Anything outside `RESOLVED` and `CORPORATE_ACTION_EXEMPT` is a gap in the seed data for what was actually traded. `GET /user-charges/user/:email/gaps` lists them |
| `priced` | What the totals in §5b.4 will be built from |

### 5b.3 Confirm a row was written

```bash
curl -sS "$BASE/user-charges/user/$USER_EMAIL" -H "Authorization: Bearer $TOKEN" \
  | jq '.data | map({transactionId, event, assetType, resolution, totalCharges}) | .[0:5]'
```

A row per trade, each carrying the resolution that produced it. No rows at all means the flag did not
take.

### 5b.4 Read the reconciliation report — the actual deliverable

```bash
curl -sS "$BASE/user-charges/user/$USER_EMAIL/reconciliation" -H "Authorization: Bearer $TOKEN" \
  | jq '.data | {totalComputed, totalEntered, totalDelta, comparableCount, unresolvedCount, transactionsWithoutComputation}'
```

**Read the three counts before the three totals.** They decide whether the totals mean anything:

| Field | What a non-zero value tells you |
|---|---|
| `unresolvedCount` | The seed data has no card for some of what was traded. Those rows computed zero for a stated reason and are **excluded** from the totals — check them in 5b.5 before trusting the delta |
| `transactionsWithoutComputation` | Shadow recording did not reach those trades. Expected for anything traded before the flag went on; unexpected otherwise, and worth investigating before reading a delta |
| `comparableCount` | How many rows the totals are actually built from. If this is much smaller than the row count, the delta is describing a minority of the portfolio |

Then the per-trade detail:

```bash
curl -sS "$BASE/user-charges/user/$USER_EMAIL/reconciliation" -H "Authorization: Bearer $TOKEN" \
  | jq '.data.rows | map(select(.comparable)) | sort_by(-(.delta | fabs)) | .[0:10]
        | map({transactionId, stockCode, assetType, computed, entered, delta})'
```

Largest absolute differences first — that is where an explanation is either found or owed.

### 5b.5 The rows the totals left out

```bash
curl -sS "$BASE/user-charges/user/$USER_EMAIL/reconciliation" -H "Authorization: Bearer $TOKEN" \
  | jq '.data.rows | map(select(.comparable | not)) | map({transactionId, resolution, note})'
```

Every one carries a `note` saying why it was not compared. Two shapes appear: the computation did not
resolve, or no transaction with that id is on file.

### 5b.5b Known-good baselines

Two runs, kept because they answer different questions.

**A synthetic run (2026-09-09, local replica set)** — four hand-driven V2 trades, useful for checking
the plumbing works before pointing anything at real data:

| Trade | Computed | Entered | Delta |
|---|---|---|---|
| RELIANCE ×100 @ ₹1,000 BUY, 2025-06-10 | 118.74 | 125.50 | −6.76 |
| PARAGPARIKHFLEXICAP ×500 @ ₹75.25 BUY | 2.00 | 0.00 | +2.00 |
| RELIANCE ×100 @ ₹1,200 SELL, 2025-09-15 | 140.41 | 140.00 | +0.41 |
| INFY ×10 @ ₹800 BUY, **2019**-04-01 | 0.00 | 45.00 | *excluded* |

```
{ "totalComputed": 261.15, "totalEntered": 265.50, "totalDelta": -4.35,
  "comparableCount": 3, "unresolvedCount": 1, "transactionsWithoutComputation": 0 }
```

The entered figures here were invented, so the deltas are not evidence about the engine. What this
run *does* prove is the exclusion rule: adding the 2019 trade moved `unresolvedCount` 0 → 1 and left
`totalDelta` at −4.35. Subtracted, it would have read −49.35 and looked like a ₹45 undercharge.

**The real run (2026-09-09, `it-staging`, 319 transactions)** — backfilled, then reconciled:

```
backfill:  transactionsRead 319 | priced 319 | skipped 0 | sellsWithNoLotsFound 0
           RESOLVED 45 | NO_SCHEDULE 227 | NO_INSTRUMENT_PROFILE 43 | CORPORATE_ACTION_EXEMPT 4
report:    totalComputed 240.62 | totalEntered 5.32 | totalDelta 235.30
           comparableCount 49 | unresolvedCount 270 | transactionsWithoutComputation 0
```

**Read `phase-b-reconciliation-findings.md` before drawing anything from those deltas.** The entered
side is ₹5.32 across 49 trades — 37 of them exactly ₹0.01 — so the delta is the computed total
measured against a field nobody filled in, not a disagreement about a charge.

### 5b.6 Deltas you should expect, and what each means

A non-zero delta is **not** automatically a defect on either side. Before reporting one, rule these
out:

| Cause | How it shows |
|---|---|
| The user typed a rounded figure | Small, unsigned, scattered — a few paise to a rupee across many trades |
| **Segment**: a trade priced in the wrong segment | **Fixed in Chunk 10b** — `AssetRequest`, `TransactionEntity`, `AssetEntity` and `ProfitLossContext` all carry `TradeSegment`, defaulting `DELIVERY`. Anything recorded *before* that still reads as delivery, so an intraday trade from an older row reconciles against a delivery card |
| The old GST defect (D1) | The engine is **lower** on sells by roughly ₹17 per ₹1,00,000, because the superseded implementation applied GST over a bucket including STT and stamp duty. This delta is the engine being right |
| Depository deduplication | A second sell of the same scrip on the same day carries no DP charge. If the user entered one on both, the engine is lower by the DP charge and its tax |
| A rate card generation boundary | A trade near 2026-03-01 or 2026-06-19 prices against a different generation than the user may have assumed. `scheduleCode` on the row names which card answered |

**Phase C does not start until every delta on this list is either explained or fixed.**

---

## 6. Failure cases worth confirming once

```bash
# No token → 401
curl -sS -o /dev/null -w '%{http_code}\n' "$BASE/charge-schedules/unverified"

# Missing transactionDate → 400, naming the field
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"brokerName":"ZERODHA","assetType":"EQUITY","segment":"DELIVERY","event":"SELL",
       "price":1000,"quantity":100}' | jq -r '.message // .error // .'

# A broker with no card at all → NO_SCHEDULE, not an error
curl -sS -X POST "$BASE/charges/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"brokerName":"UPSTOX","assetType":"MUTUAL_FUND","event":"SELL","stockCode":"HDFCLIQUID",
       "price":100,"quantity":1000,"transactionDate":"2025-06-02"}' \
  | jq '.data | {resolution, totalCharges}'
```

---

## 7. AC-2 — correcting a rate

This is the actual job. For each card in the step 3.2 worklist, compare its rules against the
broker's published charges page, then publish a corrected version.

**Publishing does not edit the old card.** It closes the incumbent's validity window and inserts a
new one, so trades already priced under the old rates stay priced that way — which is the whole
point of the temporal model.

### 7.1 Take the current card as your starting document

```bash
curl -sS "$BASE/charge-schedules/ZERODHA_EQ_DELIVERY_2025_04" -H "Authorization: Bearer $TOKEN" \
  | jq '.data' > zerodha-delivery-current.json
```

### 7.2 Edit it

- correct each `rate` / `flatAmount` against the broker's page
- **new `scheduleCode`** — e.g. `ZERODHA_EQ_DELIVERY_2026_09`
- **new `startDate`** — the date the corrected rates take effect
- `"endDate": null`
- **`"verifiedOn": "2026-09-08"`** — the date you checked. This is what removes it from the worklist
- confirm `sourceUrl` points at the page you actually read
- drop the `id` field, and drop `PLACEHOLDER` from each `notes`

### 7.3 Publish it

```bash
curl -sS -X POST "$BASE/charge-schedules" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d @zerodha-delivery-corrected.json \
  | jq '.data | {scheduleCode, startDate, endDate, verifiedOn, status}'
```

A malformed card is rejected with every problem listed at once, naming the rule each belongs to.

### 7.4 Confirm the incumbent was superseded

```bash
curl -sS "$BASE/charge-schedules?broker=ZERODHA" -H "Authorization: Bearer $TOKEN" \
  | jq '.data | map({scheduleCode, startDate, endDate, verifiedOn})'
```

The old card now has an `endDate`; its `status` is deliberately **still `ACTIVE`**, because it must
go on pricing the trades inside its own window.

### 7.5 Re-price a trade and see what moved

Re-run step 4.1. Any figure that changed is a trade whose charges the correction affected.

### 7.6 Withdraw a card without replacing it

```bash
curl -sS -X PATCH "$BASE/charge-schedules/ZERODHA_EQ_DELIVERY_2025_04/close?endDate=2026-03-31" \
  -H "Authorization: Bearer $TOKEN" | jq '.data | {scheduleCode, endDate, status}'
```

### 7.7 When the worklist is empty, AC-2 is closed

```bash
curl -sS "$BASE/charge-schedules/unverified" -H "Authorization: Bearer $TOKEN" | jq '.data | length'
```

`0` closes AC-2 — as it was closed on 2026-09-08.

**It will not stay `0`.** A card's verification ages out after 90 days and returns here, which is
deliberate: NSE revised the transaction charge and Zerodha cut its depository fee inside the window
of cards that had already been signed off. Re-running this quarterly is the job, not a one-off.

---

## 7a. Before and after any deploy — check for drift

```bash
curl -sS "$BASE/charge-schedules/drift" -H "Authorization: Bearer $TOKEN" | jq '.data'
```

Expect **`[]`** on a healthy deployment.

Entries mean the shipped files and the database disagree, and the endpoint deliberately does not say
which is right:

| What you see | What it means | Fix |
|---|---|---|
| `"absent from the database"` | A card ships that this environment has never seeded | `POST /charges/seed` |
| A list of fields | A card on file differs from the file beside it | Decide which is right — see below |

A field list has two opposite causes. Either somebody corrected a rate in production through
`POST /charge-schedules` and the repository has not caught up, in which case **the database is
right** and the seed file should be updated to match. Or somebody edited a seed file expecting a
deploy to apply it, in which case **the file is right** and it needs publishing through the API —
because the seeder will never overwrite a card already on file, by design.

**The rule that keeps this list empty is ADR-26: never edit a card that has been deployed.** A rate
change is a new generation with a new `scheduleCode`, applied by one call to `POST /charges/seed`.

## 8. Three things to know before you start

1. **A rate card written straight to MongoDB is invisible to the engine.** The resolver caches by
   scope and date, and only `POST /charge-schedules` and the close endpoint evict it. Always go
   through the API.
2. **Scheme profiles have no publishing endpoint at all** — they are seeded at startup and nothing
   else evicts `ChargeInstrumentResolver`. A profile added by hand needs an application restart.
3. **Shadow recording never fails a trade.** Every error inside the gateway is caught and logged with
   the transaction id, and the trade saves regardless. So a `user_charges` row that never appeared is
   reported by its *absence* — `transactionsWithoutComputation` in the reconciliation report — not by
   anything the user saw go wrong. Grep the logs for `Shadow charge recording failed`.
