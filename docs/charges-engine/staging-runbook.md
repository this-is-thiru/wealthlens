# Charges Engine — staging runbook

Every request the charges engine answers, in the order they make sense to run, with the numbers each
one should return. Written for the AC-2 rate verification, which the repository owner scheduled for
staging after the merge (ADR-18), but the read-only half is also the fastest way to confirm a
deployment came up with its seed data intact.

**Expected figures come from the golden fixtures**, so a mismatch means either the deployment is not
running this branch, or the rates have already been corrected — which is the point of the exercise.

> **The rates below are placeholders.** They are what the shipped cards say, not what Zerodha
> charges. Step 7 is where that gets fixed.

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

Expect **all 6 shipped cards**, every one with `"verifiedOn": null`:

| scheduleCode | assetType | segment |
|---|---|---|
| `ZERODHA_EQ_DELIVERY_2025_04` | EQUITY | DELIVERY |
| `ZERODHA_EQ_INTRADAY_2025_04` | EQUITY | INTRADAY |
| `ZERODHA_MF_2025_04` | MUTUAL_FUND | — |
| `ZERODHA_MAINTENANCE_2025_04` | — | — |
| `UPSTOX_EQ_DELIVERY_2025_04` | EQUITY | DELIVERY |
| `FYERS_EQ_DELIVERY_2025_04` | EQUITY | DELIVERY |

**This list emptying is what closes AC-2.**

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
SEBI_FEE 0.10, GST 4.15` — **total ₹52.22**. No DP, no stamp duty on a sell.

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
Totals `6.91`, `52.22`, `1454.73`.

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

### ⚠️ What `/charges/simulate` cannot show you

**Exit load will always come back ₹0 from this endpoint.** `ChargeSimulationRequest` carries no FIFO
lots, so `ChargeSimulationService` passes an empty lot list, and a `perLot` rule evaluates zero
times. The charge is real — the golden fixtures and `ChargesIntegrationTest` price it through the
engine — but the API has no way to hand it the lots it needs.

The same limitation hides depository-charge deduplication: simulate checks recorded charges, and in
Phase A nothing in the trade path records any, so DP is always priced as a first occurrence.

Both are Phase A's shape, not defects. Adding `lots` to the simulate request would make exit load
demonstrable here — worth doing if you want AC-6 visible in staging rather than only in the suite.

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

## 6. Failure cases worth confirming once

```bash
# No token → 403
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

`0` closes AC-2. Tick it in `implementation-checklist.md`, and update ADR-18's consequence.

---

## 8. Two things to know before you start

1. **A rate card written straight to MongoDB is invisible to the engine.** The resolver caches by
   scope and date, and only `POST /charge-schedules` and the close endpoint evict it. Always go
   through the API.
2. **Scheme profiles have no publishing endpoint at all** — they are seeded at startup and nothing
   else evicts `ChargeInstrumentResolver`. A profile added by hand needs an application restart.
