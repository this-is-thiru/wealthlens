# AC-2 — rate verification

**Checked on 2026-09-08** against each broker's published charges page. This is the comparison AC-2
asks for: every shipped rate against the source, with the source quoted so the claim can be
re-checked rather than taken on trust.

**Closed on 2026-09-08.** The two questions in §4 were decided by the repository owner and the
corrections are in the seed files; §6 is what remains operationally.

## Sources

| Broker | Page | Read |
|---|---|---|
| Zerodha | `https://zerodha.com/charges` | 2026-09-08 |
| Upstox | `https://upstox.com/brokerage-charges/` | 2026-09-08 |
| Fyers | `https://fyers.in/charges-list` | 2026-09-08 |
| NSE transaction charge revision | NSE circular, 2026-02-27, effective 2026-03-01 | via search |
| NSE IPFT revision | raised from ₹0.01/crore to ₹10/crore w.e.f. 2023-04-01 | via search |
| Zerodha AMC | `support.zerodha.com` — ₹300 + 18% GST annually | via search |

---

## 1. The headline: the placeholders were mostly right, and two rates moved inside the window

The shipped cards start **2025-04-01**. Every page above states **today's** rate, and two of the
figures on them changed *after* the cards' start date:

| What | Was | Became | From |
|---|---|---|---|
| NSE equity transaction charge | 0.00297% | **0.0030699%** | **2026-03-01** (NSE circular 2026-02-27) |
| Zerodha DP, broker component | ₹10.25 (₹13.50 total, ₹15.93 with GST) | **₹9.50** (₹13.00 total, ₹15.34 with GST) | 2026, exact date not published |

So `0.00297` and `13.5` on the 2025-04 cards are **correct for the window those cards cover**. The
right correction is not to edit them — that would reprice every 2025 trade with a rate that did not
exist then, which is the defect ADR-12 was written to prevent. It is to **publish successor cards**
and let the incumbent close.

This is the temporal model doing exactly what it was built for, on its first contact with reality.

---

## 2. Verified correct, no change needed

| Card | Rule | Shipped | Published |
|---|---|---|---|
| `ZERODHA_EQ_DELIVERY_2025_04` | BROKERAGE | flat ₹0 | "Zero Brokerage" |
| | STT | 0.1%, both sides | "0.1% on buy & sell" |
| | SEBI_FEE | 0.0001% | "₹10 / crore" |
| | STAMP_DUTY | 0.015%, buy only | "0.015% or ₹1500 / crore on buy side" |
| | GST | 18% over brokerage, exchange, SEBI, IPFT, DP | "18% on (brokerage + SEBI + transaction)"; DP and IPFT are quoted GST-inclusive on the page, so the base list is equivalent |
| `ZERODHA_EQ_INTRADAY_2025_04` | BROKERAGE | 0.03%, MIN ₹20 | "0.03% or Rs. 20/executed order whichever is lower" |
| | STT | 0.025%, sell only | "0.025% on the sell side" |
| | STAMP_DUTY | 0.003%, buy only | "0.003% or ₹300 / crore on buy side" |
| `ZERODHA_MF_2025_04` | STT | 0.001%, sell, equity-oriented only | statutory rate; Zerodha charges nothing of its own on direct MF |
| | STAMP_DUTY | 0.005%, buy | statutory rate |
| `ZERODHA_MAINTENANCE_2025_04` | AMC | ₹300 + 18% GST | "₹300 + 18% GST annually" |
| `FYERS_EQ_DELIVERY_2025_04` | DP | ₹12.50 + GST | "₹12.5 + GST per scrip (₹3.5 CDSL + ₹9 FYERS)" |

---

## 3. Genuine defects — wrong when the card was written, not merely stale

These are not rate drift. They were wrong on 2025-04-01 too.

### 3.1 Fyers brokerage rate is 0.1% and should be 0.3%

Shipped: `TURNOVER 0.1%, MIN, flat ₹20`. Published: **"₹20 or 0.3% per executed order, whichever is
lower"**.

The *shape* is right — I first recorded this as a flat-fee modelling defect and that was wrong; the
card already uses the aggregator. The rate is the defect. Both cards agree at ₹1,00,000 because the
₹20 cap binds either way, which is why the golden fixture never saw it. Below ₹20,000 they diverge:
a ₹1,000 trade is charged **₹1.00** and should be **₹3.00**.

Caught by simulating a ₹1,000 trade against the running application rather than by reading the card,
which is worth remembering — the fixture and my own reading of the file both missed it.

### 3.1b Upstox brokerage is a percentage and should not be

Shipped: `TURNOVER 0.1%, MIN, flat ₹20`. Published: **"₹20 per executed order"** — flat, with no
percentage alternative.

Same ₹20 at ₹1,00,000, and **undercharging every trade below ₹20,000**: a ₹1,000 trade is charged
₹1.00 against a real ₹20.00. Undercharging is the more dangerous direction here, because it
understates a cost basis rather than producing a complaint.

### 3.2 IPFT is missing from three of the four equity cards

Only `ZERODHA_EQ_DELIVERY_2025_04` carries an IPFT rule. Zerodha intraday, Upstox and Fyers all
levy it and all three cards omit it. Every one of those pages lists it explicitly, and Upstox and
Fyers both include it in their GST base.

### 3.3 Upstox DP charge

Shipped: **₹18.50**. Published: **₹20.00** before GST (₹3.5 CDSL + ₹16.5 Upstox).

### 3.4 Upstox and Fyers exchange transaction charges need the same successor treatment

Both cards carry `0.00297`. Fyers' page now states `0.0030699%`; Upstox's page still says
`0.00297%`, which given the NSE circular appears to be a stale page rather than a different rate —
the transaction charge is set by the exchange, not the broker, so all three should agree.

---

## 4. Two things I cannot decide

### 4.1 The IPFT rate — the broker pages disagree with each other

| Source | Says | As a percentage |
|---|---|---|
| Zerodha page | "₹0.01 per crore + GST" | 0.000001% |
| Fyers page | "NSE IPFT ₹0.01/Crore" | 0.000001% |
| Upstox page | "₹0.10 per Lakh" | **0.0001%** |
| NSE (via search) | raised ₹0.01/crore → **₹10/crore** w.e.f. 2023-04-01 | **0.0001%** |
| **Shipped cards** | 0.0001% | — |

Two pages say one thing and two sources say another, a thousandfold apart. The shipped value agrees
with Upstox and with the NSE revision, and the reading that makes sense of all four is that Zerodha's
and Fyers' pages carry a pre-2023 figure nobody updated.

On ₹1,00,000 the difference is ₹0.10 against ₹0.0001 — small in rupees, and exactly the kind of
figure that is never noticed and never right.

**Decided 2026-09-08: take ₹10 per crore**, on the reading that Zerodha's and Fyers' pages carry a
pre-2023 figure nobody updated. That is what the cards already had, so no value changed; what changed
is that the rule's `notes` now state the basis rather than the word PLACEHOLDER. Confirming it
against a real contract note remains worth doing, and revising it is a rate change like any other —
a new card, no Java.

### 4.2 What to do about the window — this is the decision that shapes everything else

Since two rates moved inside the cards' validity window, there are two coherent approaches:

**Decided 2026-09-08: successor cards.** Eleven cards now ship in three generations:

| Scope | Windows |
|---|---|
| Zerodha EQUITY/DELIVERY | `2025_04` to 2026-02-28, `2026_03` to 2026-06-18, `2026_06` open |
| Zerodha EQUITY/INTRADAY | `2025_04` to 2026-02-28, `2026_03` open |
| Upstox EQUITY/DELIVERY | `2025_04` to 2026-02-28, `2026_03` open |
| Fyers EQUITY/DELIVERY | `2025_04` to 2026-02-28, `2026_03` open |
| Zerodha MUTUAL_FUND, Zerodha maintenance | one generation each, open — neither carries an exchange charge |

The 2025 cards keep the rates that applied in their own window; the §3 defects, which were wrong in
every window, are fixed in all of them. `everyBrokersCardsFormOneUnbrokenTimeline` asserts each
generation ends the day before the next begins, because a gap prices trades inside it at nothing.

Three fixtures pin the same ₹1,00,000 Zerodha sell across the three windows at **₹119.67**,
**₹119.79** and **₹119.20**.

### 4.3 What `verifiedOn: 2026-09-08` actually claims, on a 2025 card

Every card now carries that date, including the 2025 generation, and it is worth being exact about
what it asserts: **that every figure on it was compared, on 2026-09-08, against the best available
evidence for the window that card covers.** For the current generation that evidence is the broker's
page. For the 2025 generation it is the page plus a documented change date — the NSE circular for the
transaction charge, secondary reporting for Zerodha's depository fee.

It does **not** mean anybody read the broker's page as it stood in April 2025. Those pages are not
retrievable, so under a stricter reading the 2025 cards could never be verified at all and the
worklist would never empty. The looser reading is the useful one, provided it is written down —
which is what this section is.

---

---

## 4a. Executed against the running application, 2026-09-08

The runbook's read-only and simulate sections were run against the app on `localhost:8080`. Nothing
was persisted; the AMC cycle in §5 of the runbook was **not** run, because one call bills every
account of that frequency across every user.

| Check | Result |
|---|---|
| `GET /charge-catalogue` | 12 codes, as seeded |
| `GET /charge-schedules/unverified` | all 6 cards, every `verifiedOn` null — the AC-2 worklist |
| `GET /charge-schedules?broker=ZERODHA` | 4 cards, all ACTIVE, all open-ended |
| 10 simulate cases against the golden expectations | **10/10 exact match**, line by line and in total |
| Exit load across two lots | `EXIT_LOAD 400.00` + `STT 1.00` = ₹401.00, `instrumentId` populated |
| Lots that do not add up | HTTP 400, message naming 900 against 1000 |
| Liquid fund without lots | `NO_MATCHING_RULES` |
| Unseeded scheme | `NO_INSTRUMENT_PROFILE` |
| Unauthenticated read | **401** — the runbook said 403; corrected there |

So the engine, the seed data and the deployment agree with the test suite exactly. Every defect in
§3 is in the *rate data*, not in the machinery that applies it.

## 5. What was done

1. **Five defects fixed** — Fyers brokerage 0.1% → 0.3%; Upstox brokerage from `MIN(0.1%, ₹20)` to a
   flat ₹20; Upstox depository fee ₹18.50 → ₹20.00; IPFT added to the Zerodha intraday, Upstox and
   Fyers cards and to their GST bases.
2. **Five successor cards** for the NSE transaction charge and Zerodha's depository fee.
3. **Golden fixtures recomputed** — six changed, four added. The two new small-trade fixtures are the
   ones that matter: at ₹1,00,000 the ₹20 cap binds whatever the percentage says, which is exactly
   how both brokerage errors shipped and passed. Every figure was computed by hand from the corrected
   cards and matched the engine on the first run.
4. **`verifiedOn` and `sourceUrl` set on all eleven cards**, and the word PLACEHOLDER removed from
   every rule note — a rule calling itself a placeholder on a card claiming to be verified is one of
   the two lying, and `noShippedRuleStillCallsItselfAPlaceholder` now fails the build for it.
5. `everyCardSaysWhereItsRatesCameFromAndAdmitsTheyAreUnverified` was **inverted**: it asserted a null
   `verifiedOn` on every card, which was right until today and is now exactly backwards.

**785 tests green**, both JaCoCo gates passing.

---

## 6. One thing this does not do by itself

**A database that already ran the old seeder keeps the old cards.** `ChargeSeederService` is
idempotent by `scheduleCode` — deliberately, so an operator's edits are not overwritten on every
restart — so on the next start it will *add* the five new cards and leave the six existing ones
exactly as they were, uncorrected and unverified. Confirmed against the running application: it still
reports six unverified cards.

The corrected 2025 cards therefore reach an existing environment only if the documents are removed
first:

```js
// against the target database, before restarting
db.charge_schedules.deleteMany({ schedule_code: /_2025_04$/ })
```

Safe here because nothing in the live trade path prices anything yet, so no stored charge references
these cards. **It stops being safe the moment Phase B starts recording**, after which a corrected
card must be published through `POST /charge-schedules` — which supersedes rather than overwrites —
and the rows priced by the old one recomputed.

Worth stating plainly because it is the kind of thing that is discovered in production: seeding is a
first-run convenience, not a deployment mechanism.

**Since resolved as ADR-26.** A deployed card is never edited — a rate change ships as a new
generation with a new `scheduleCode`, which the seeder applies on the next deploy with no manual
step. The `deleteMany` above is the one-off cost of having edited cards that were already deployed,
and should never be needed again. The seeder now warns when a card on file differs from its shipped
file, and `GET /charge-schedules/drift` lists the differences on demand, so the two can no longer
diverge unnoticed.
