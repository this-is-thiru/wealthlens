# Charges Engine — production runbook

**Every step that has to be run by hand, in the order it has to be run.** Nothing here happens
automatically, and nothing here is optional.

**Read this before deploying.** The engine ships with `shadow-recording` and `authoritative` both
**on**, so the first trade after deployment is priced by it. One step — seeding — has to happen
before that, or trades are recorded as charged nothing.

---

## 0. The one thing that will bite you

**`POST /charges/seed` is not automatic.** Seeding stopped being a startup hook at ADR-27, so a
database that has never been seeded holds no rate cards. With the flags on and no cards:

- every trade resolves `NO_SCHEDULE` and is charged **₹0**;
- because `authoritative` is on, that **₹0 becomes the cost basis**;
- nothing throws, no health check fails, and the only trace is a gaps report.

The application warns loudly at startup when it detects this (`ChargeReadinessAuditor`) — grep the
logs for `NO RATE CARDS`. But the warning is a backstop, not the plan. Step 2 is the plan.

---

## 1. Before deploying

| # | Action | Why |
|---|---|---|
| 1.1 | Set `MONGO_USER`, `MONGO_PASSWORD`, `JWT_SECURITY_KEY` | The app will not start without the JWT key — deliberate, a defaulted signing key is a forged-token vulnerability |
| 1.2 | Confirm MongoDB is a **replica set** | Multi-document `@Transactional` writes need one. Without it `app.mongodb.transactions-enabled` cannot help, and `TransactionSafetyAuditor` will warn at startup |
| 1.3 | Ensure a `SUPER_USER` account exists | Steps 2 and 5 require one. Seeding, publishing rate cards, the AMC cycle and the backfill are all `SUPER_USER`-only |

There is **no data migration**. The superseded implementation and its three collections
(`broker_charges`, `user_broker_charges`, `asset_management_details`) were deleted in Chunk 11, and
nothing in the engine reads them.

---

## 2. Immediately after deploying — seed the rate cards

**Required. Run before the first trade.**

```bash
curl -sS -X POST "$BASE/charges/seed" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
```

Expect 12 catalogue codes, 11 rate cards and 2 scheme profiles. Idempotent — a second run writes
nothing and says so, so it is safe on a deployment checklist.

**Then confirm it took:**

```bash
# Must be empty: the shipped files and the database agree
curl -sS "$BASE/charge-schedules/drift" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'

# Must be empty: every card carries a verifiedOn no older than 90 days
curl -sS "$BASE/charge-schedules/unverified" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
```

`unverified` returning rows is not a failure — it means rate verification is due. ADR-26 makes that
a standing obligation, not a one-off.

---

## 3. Verify before letting real trades through

```bash
# A known figure: Zerodha equity delivery, ₹1,00,000 sell
curl -sS -X POST "$BASE/charges/simulate" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "brokerName":"ZERODHA","assetType":"EQUITY","segment":"DELIVERY","event":"SELL",
    "exchange":"NSE","transactionDate":"2025-06-10","quantity":100,"price":1000
  }' | jq '.data'
```

`staging-runbook.md` §4 holds the expected figure for this and every other shipped card, and §4.7b
covers the three rate-card generations — the same trade prices differently by date, which is
correct.

---

## 4. Registering demat accounts (only if AMC is wanted)

AMC is **not** billed unless an account is registered. No account, no AMC charge — silently, because
there is nothing to bill.

```bash
curl -sS -X POST "$BASE/charge-accounts/user/$USER_EMAIL" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "brokerName":"ZERODHA","dematAccountId":"1208160000000001","accountHolder":"self",
    "amcFrequency":"ANNUALLY","openedOn":"2024-01-01"
  }'
```

---

## 5. Recurring manual operations

Nothing below is scheduled. Each is a deliberate act, by a `SUPER_USER`.

| Operation | Command | Cadence | What happens if it is never run |
|---|---|---|---|
| **AMC cycle** | `POST /charges/amc/impose` | quarterly / annually, per account frequency | No maintenance charge is ever billed. Idempotent per account and period, so running it twice bills once |
| **Rate verification** | `GET /charge-schedules/unverified` | every 90 days | Cards silently age. The endpoint returns anything older than 90 days, which is the reminder |
| **Drift check** | `GET /charge-schedules/drift` | every deploy | A card edited in the repository never reaches a database that already seeded it — the seeder skips it. Drift is how that becomes visible |
| **Publishing a rate change** | `POST /charge-schedules` | when a broker changes rates | **Never edit a deployed card** (ADR-26). Publish a successor; it supersedes the incumbent in the same transaction |

---

## 6. Optional: pricing history that predates the deployment

Trades made before this deployment carry no computed charge, and **they are not repriced
automatically** — ADR-32 settles that the cutover applies forward only.

If a complete charge history is wanted for a user:

```bash
curl -sS -X POST "$BASE/charges/backfill/user/$USER_EMAIL" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
```

**It writes** — one `user_charges` row per priced transaction, and nothing else. No cost basis, no
P&L figure and no transaction document is touched. Reversible by deleting those rows. Safe to
re-run: a row is keyed on `{email, transactionId}` and replaced rather than appended.

Expect `NO_SCHEDULE` on anything before **2025-04-01** — every shipped card starts there. On the
one real history measured, that was 227 of 319 trades. Closing it needs historical card generations,
which is the [priced-portfolio epic](../epics/priced-portfolio.md), not a deployment step.

---

## 7. If something goes wrong

**Stop the engine without a deploy:**

```yaml
app:
  charges:
    engine-enabled: false
```

Simulate, backfill and the AMC cycle answer **503**; nothing is recorded. Reads keep working —
charge history, gaps and the reconciliation report all still respond, which is what you need in
order to decide whether to turn it back on.

**Stop only cost-basis changes**, keeping the engine recording for comparison:

```yaml
app:
  charges:
    authoritative: false
```

New buys go back to storing the client-supplied `brokerCharges`. Trades already written keep the
cost basis they were given — nothing rewrites a stored `AssetEntity`.

**What to look at:**

| Symptom | Where to look |
|---|---|
| Charges are zero | `GET /user-charges/user/{email}/gaps` — it names the reason per trade |
| Startup warned about rate cards | Run step 2. Grep logs for `NO RATE CARDS` |
| A charge looks wrong | `GET /user-charges/user/{email}/transaction/{id}` — the contract note, line by line, with the `scheduleCode` that priced it |
| Figures disagree with the client's | `GET /user-charges/user/{email}/reconciliation` |

---

## 8. Known limits — recorded, not defects

These are deliberate. Full list in `README.md` §10.

- **A fresh database prices nothing until step 2 runs.**
- **Nothing before 2025-04-01 can be priced** — no card covers it.
- **Only two mutual-fund schemes have exit-load profiles.** Every other fund resolves
  `NO_INSTRUMENT_PROFILE` and accrues no exit load; it is recorded, never fatal (ADR-24).
- **A rate card written directly to MongoDB is invisible** until the resolver cache is evicted. Only
  `POST /charge-schedules` and the close endpoint evict. Always go through the API.
- **Scheme profiles have no publishing endpoint** — they arrive via seeding, and a profile added by
  hand needs a restart.
- **No `POST /charges/recompute`.** Amending a card that has already priced charges has no safe
  mechanism; it is designed in tech-spec §14.4 and not built.
- **V1 `buyStock` / `sellStock` do not use the engine.** They are in live use and deliberately
  untouched. Only the V2 endpoints (`/portfolio/user/{email}/transaction/v2`) are priced.
