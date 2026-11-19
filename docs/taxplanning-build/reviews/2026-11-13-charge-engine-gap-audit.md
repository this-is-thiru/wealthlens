# W-264b — gap audit: charge engine

Session 87, 2026-11-13. Plan rev 1 approved as recommended (D-611: C1a, C2a, C3a, C4a).

**How it was driven:**
- Real HTTP and the real filter chain, on the Testcontainers harness (`mongo:8.0.4` replica set, `integration-test`
  profile).
- The **shipped** cards, seeded the way a deployment is seeded (`POST /charges/seed`, `SUPER_USER`), before every
  test.
- The charge flags as `application.yaml` sets them (all on), except in the three flag classes.
- Nothing ran against a real database, and this item fixes nothing.

**Status:** complete. Every area of the plan was driven, and §5 says what could not be.

## 1. Scenarios run

The plan's numbering is used, plus the reach checks added while reading the code (40, 41). Expected values were
written before each run (§2). Each actual value was read from the probe transcript or a kept test.

| # | Scenario | Expected | Actual | Verdict |
|---|---|---|---|---|
| 1 | seed, then `GET /charge-catalogue`, `/charge-schedules/drift`, `/charge-schedules/unverified` | catalogue present; drift `[]`; unverified `[]` | catalogue 200; drift `[]`; unverified `[]` | ok |
| 1 | `GET /charge-schedules` with no `broker` | 400 naming the parameter | 400 "Required request parameter 'broker' is not present" | ok |
| 4 | publish with a code not in the catalogue; a lowercase `brokerage` | 400, readable | 400 "Charge schedule PROBE_BAD_CODE is invalid: rule NOT_IN_CATALOGUE is not in the charge catalogue"; the lowercase one the same | ok |
| 4 | publish with a USER token | 403 | 403 (with a well-formed body) | ok |
| 4 | publish a body missing a primitive (`requiresInstrumentProfile`, `perLot`) | 400, readable | 400 "JSON parse error: Cannot map \`null\` into type \`boolean\` (set \`DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES\` to 'false' to allow)" | **G3** |
| 5 | close `ZERODHA_EQ_DELIVERY_2026_06` on 1 Jul 2026, then simulate a delivery buy on 1 Aug 2026 | no broker card: `NO_SCHEDULE`, listed as a gap | `RESOLVED` on `ZERODHA_MAINTENANCE_2025_04`, statutory lines only (₹12.01) | **G1** |
| 5 | close as USER; close an unknown card; close before its start | 403; 400; 400 | 403; 400 "No charge schedule with code NOPE"; 400 "… that end date precedes its start date of 2025-04-01" | ok |
| 8, 12 | the base buy (§2) | ₹12.38, lines as §2 | ₹12.38, every line equal | ok, kept |
| 12 | the base sale (§2) | ₹27.34 | ₹27.34, every line equal | ok, kept |
| 9 | an NSE-scoped Zerodha card with brokerage ₹5, then an NSE trade and a BSE trade | NSE: that card, BROKERAGE 5, GST 0.90; BSE: the general card | as expected | ok, kept |
| 11 | a buy on 28 Feb 2026 (last day of `…_2025_04`) and 1 Mar 2026 (first of `…_2026_03`) | each its own card; EXCHANGE_TXN 0.31 on 1 Mar | as expected | ok, kept |
| 13 | intraday buy, 10 at ₹1,000 | ₹3.92 (§2) | ₹3.92 | ok |
| 14 | a Fyers trade on 1 Jan 2024 (before every card) | `NO_SCHEDULE`, ₹0 | `NO_SCHEDULE`, ₹0 | ok |
| 14 | simulate with no `segment` | not priced as delivery silently | `NO_MATCHING_RULES`, ₹0 | ok (says so) |
| 15 | `price` "abc"; no body | 400, readable | 400 "JSON parse error: Cannot deserialize value of type \`double\` from String \"abc\" …"; 400 "Required request body is missing: public com.thiru.wealthlens.brokercharges.dto.response.ChargeBreakdownResponse com.thiru.wealthlens.brokercharges.controller.ChargeSimulationController.simulate(…)" | **G3** |
| 15 | `{}`; quantity −10; no token | 400; 400; 401 | 400 "brokerName is required to pick a rate card"; 400 "quantity must be greater than zero"; 401 | ok |
| 16 | V2 delivery buy (entered ₹15) and sale; the contract notes | one `user_charges` row each, ₹12.38 and ₹27.34 | as expected | ok |
| 17 | V2 intraday buy | the intraday card, ₹3.92, the lot costs ₹3.92 (authoritative) | as expected | ok, kept |
| 18 | V2 buy on 1 Jan 2024 | trade saved; `NO_SCHEDULE`; listed in `/gaps` | as expected | ok |
| 19 | `/reconciliation` after 16 – 18 | buy computed 12.38, entered 15, delta −2.62; the 2024 trade not compared | as expected, "not compared: the computation resolved as NO_SCHEDULE" | ok |
| 20 | `shadow-recording=false`, V2 buy with ₹15 entered | saved; nothing recorded; lot ₹15 | as expected | ok, kept |
| 21 | `authoritative=false` | recorded ₹12.38; lot ₹15 | as expected | ok, kept |
| 22 | `authoritative=true`, `shadow-recording=false` | nothing recorded; lot ₹15 (CLAUDE.md: only while shadow is on) | as expected | ok, kept |
| 23 | V1 delivery buy | CLAUDE.md: "the charges engine is wired into V2 only" | **priced and recorded** (₹12.38, `RESOLVED`); the lot costs the entered ₹0 | **G4** |
| 24 | V1 intraday buy (PS-13) | the intraday card | `ZERODHA_EQ_INTRADAY_2025_04`, ₹3.92 | ok, kept |
| 25 | V2 fund buys of 60 (10 Apr 2025) and 40 (10 Jun 2025) at ₹100, 100 redeemed at ₹110 on 1 May 2026 | EXIT_LOAD: the 386-day lot 0; the 325-day lot 1 % × 40 × ₹110 = **₹44.00** | the 325-day lot: base ₹4,000, **₹40.00** | **G2** |
| 26 | 10 units held exactly 365 days | 0 (PRD `prd.md:89`: "1% if held < 365 days"; bands half-open) | 0 | ok |
| 27 | a fund with no instrument profile | listed in `/gaps` | listed | ok |
| 28 | Zerodha account, annual, `impose` twice | ₹300 + GST 18 % = ₹354, once | ₹354 once; second run bills nobody | ok, kept |
| 28 | `due` as USER; bad `frequency`; no `billedThrough` | 403; 400; 400 | 403; 400 "Parameter 'frequency' must be one of [ANNUALLY, QUARTERLY], but was 'HOURLY'"; 400 | ok |
| 28 – 29 | an account opened 1 Jan 2024 and one opened 1 Oct 2025, billed through 31 Mar 2026 | — (no rule found to state it) | each billed one ₹354, covering 27 months and 6 months | **Q-2** |
| 31 | history, `/gaps`, `/reconciliation` with no charges; an unknown transaction | `[]`, `[]`, zeros; 400 | as expected; 400 "No charges recorded for transaction nope" | ok |
| 32 | my transaction id on another user's path, their token | nothing of mine | 400 "No charges recorded …" | ok |
| 33 | `from` after `to`; month 13; `assetType=STOCK` | 400 naming the values | 400 "Date range starts after it ends: 2026-01-01 is after 2025-01-01"; the others name the format and the constants | ok, kept (first) |
| 34 | another user's `/user-charges`, `/charge-accounts` (read and write) with my token | refused | 401 | ok |
| 40 | simulate BOND, GOLD_BOND, FD, INSURANCE at Zerodha; a V2 bond buy | not priced, and said so | `NO_MATCHING_RULES`; the bond buy listed in `/gaps`, not compared in `/reconciliation` | ok |

**Kept tests** (C2a):
- `ChargeGapAuditIntegrationTest` (8): the base buy, the base sale, the card boundary, specificity, V2 intraday,
  V1 intraday, AMC, and the date range.
- `ChargeAuditShadowOffIntegrationTest`, `ChargeAuditAuthoritativeOffIntegrationTest` and
  `ChargeAuditAuthoritativeWithoutShadowIntegrationTest`: 1 each.
- Their shared requests are in `AbstractChargeAuditIntegrationTest`.

## 2. Expected values, worked by hand before the runs

The cards used are `zerodha-equity-delivery-2025-04-01.json`, `statutory-equity-delivery-2025-04-01.json`,
`zerodha-equity-intraday-2025-04-01.json` and `statutory-equity-intraday-2025-04-01.json` (rates in percent),
`instruments/parag-parikh-flexi-cap-2025-04-01.json` (1 % below 365 days held, 0 from 365), and
`zerodha-maintenance-2025-04-01.json` with `statutory-account-2025-04-01.json`. GST takes its base from the
base lines after each one is rounded. That was read before the runs, at `ChargeEngine:344 – 348` (each line rounded,
then added) and `ChargeAccumulator:41 – 48` (`sumOf` adds the stored amounts).

| Line | Delivery buy, 10 × ₹1,000 | Delivery sale, 10 × ₹1,100 | Intraday buy, 10 × ₹1,000 |
|---|---|---|---|
| BROKERAGE | 0 | 0 | min(0.03 % × 10,000 = 3.00, 20) = 3.00 |
| STT | 0.1 % → 10 | 0.1 % → 11 | — (sale only) |
| EXCHANGE_TXN | 0.297 → 0.30 | 0.3267 → 0.33 | 0.30 |
| SEBI_FEE | 0.01 | 0.011 → 0.01 | 0.01 |
| IPFT | 0.01 | 0.011 → 0.01 | 0.01 |
| STAMP_DUTY | 0.015 % = 1.5 → 2 | — | 0.003 % = 0.3 → 0 |
| DP | — | 13.50 | — |
| GST 18 % | of 0.32 = 0.0576 → 0.06 | of 13.85 = 2.493 → 2.49 | of 3.32 = 0.5976 → 0.60 |
| **Total** | **₹12.38** | **₹27.34** | **₹3.92** |

- **The NSE card (scenario 9):** brokerage ₹5, with no exchange or IPFT lines because the card carries none.
  GST is 18 % of (5 + SEBI 0.01) = 0.9018 → **0.90**.
- **AMC (scenario 28):** ₹300 plus GST 18 % of 300 = 54, so **₹354**.
- **Exit load (scenario 25):** the lot bought 10 Apr 2025 and sold 1 May 2026 was held 386 days, so 0 %. The lot
  bought 10 Jun 2025 was held 325 days, so 1 % of its redemption value, 40 × ₹110 = ₹4,400, which is **₹44.00**.

## 3. Gaps

**G-1. A trade no broker card covers is reported as priced.** *(Scenario 5; reproduced twice.)*
- **Cause:** the resolver treats an unset scope as matching anything (`ChargeScheduleResolver:154 – 161`). The
  maintenance card has no asset type and no segment, so it matches an equity trade. Its only rule is for
  `AMC_CYCLE`, so it prices nothing, but the statutory card's lines make the result `RESOLVED` under
  `ZERODHA_MAINTENANCE_2025_04`.
- **Effect:** brokerage, exchange charges, IPFT and DP are silently missing. `/gaps` lists only `NO_SCHEDULE` and
  `NO_MATCHING_RULES`, so the trade does not appear there.
- **Reach:** latent with the shipped cards, because every shipped broker scope is covered without a hole from
  1 Apr 2025. The gap opens when a card is closed without a successor, or a successor starts late.
- **Why the existing test misses it:** `ChargesIntegrationTest.gapsEndpoint_surfacesATradeWithNoRateCardForItsPeriod`
  passes only because that class deletes the shipped cards, the maintenance card among them (`:128 – 129`).
- **First test:** §6.2.

**G-2. Exit load is charged on the lot's cost, not on the redemption value.** *(Scenario 25; reproduced twice.)*
- **Contract:** the PRD says exit load is a "% of redemption value" (`docs/charges-engine/prd.md:89`), and the
  slab calculator's own contract says "a percentage *of the redemption*" (`SlabChargeCalculator:18 – 19`).
- **Cause:** the V2 trade path builds each `LotSlice` with the lot's buy price
  (`ChargeRecordingGatewayImpl:145`). The engine then takes a lot's turnover as `lot.quantity() * lot.price()`
  (`ChargeEngine:409`).
- **Effect:** a fund redeemed above its cost is under-charged, and one redeemed below cost is over-charged: ₹40.00
  for ₹44.00 here.
- **`/simulate`:** it takes `lots[].price` the same way, so its lot price means cost as well.
- **First test:** §6.1.

**G-3. A body the server cannot read is answered with Jackson's own text.** *(Scenarios 4, 15; seen in both probe
runs.)*
- **Cause:** `ControllerAdviser:29 – 32` passes `HttpMessageNotReadableException.getMessage()` through unchanged.
- **Effect:** the caller reads internals, such as a library setting (`DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES`)
  and a controller method's full signature ("Required request body is missing: public com.thiru.wealthlens…
  ChargeSimulationController.simulate(…)"). CLAUDE.md requires "Messages are human-readable strings".
- **Scope:** every JSON route, not only the charge routes. W-263 (D-532) mapped the status to 400 but not the
  wording.

**G-4. CLAUDE.md says the charge engine is wired into V2 only; V1 trades are priced and recorded too.**
*(Scenario 23.)*
- **Cause:** V1's P&L update prices and records the trade when it is handed no computation
  (`ProfitAndLossService:524 – 529`, called from `PortfolioService:585`, which V1's `buyStock` reaches at `:417`).
- **What is still V2-only:** only V2 makes the computed total the cost (`PortfolioService:437`; the V1 lot keeps
  its entered charges).
- **What is wrong:** the CLAUDE.md sentence (`CLAUDE.md:159`), which the next reader of the trade path trusts.
  The behaviour itself is right.

## 4. Question for the owner

- **Q-2 (AMC periods).** A cycle bills each due account **one** flat charge for the whole span from the day after
  its last billing to `billedThrough` (`AmcChargeService:98 – 125`). So an account first billed through 31 Mar 2026
  after opening on 1 Jan 2024 pays ₹354 for 27 months. One opened on 1 Oct 2025 pays the full ₹354 for 6 months.
  Should a first run bill one charge per elapsed year, and should a part-year be prorated, or is one charge per
  run the rule?

## 5. Old entries against today's code (C3a)

| Entry | Status | Evidence |
|---|---|---|
| CE-3 resolver cache single-JVM, unbounded | still open, latent (single instance) | `ChargeScheduleResolver:61, 64` `ConcurrentHashMap`, no bound or TTL; `evictAll` in-process (`:83`) |
| CE-4 `record` read-modify-write, no version | still open, not reachable by a current caller | `UserChargeService:138 – 140` `findByEmailAndTransactionId(…).orElseGet(UserChargeEntity::new)`; no `@Version` on `UserChargeEntity` |
| CE-5 "clear all" is not an account wipe | closed in substance | the whole-account erasure exists (`AccountErasureService`, W-237d, every `PersonalDataHolder`); `/clear/all` is under `/portfolio` and its Javadoc says it is records, not the account (`UserRecordsErasureService:31 – 37`) |
| checklist `:457` the charge summary accumulates | still open, a limit accepted with a trigger ("revisit when Chunk 11 deletes the old hierarchy") | `implementation-checklist.md:457 – 460` |
| checklist `:483` | void by decision | as marked |
| checklist `:490` V2 sells write no trade outcome | fixed | `PortfolioService.allocateSaleV2` records each matched lot (`:627`, `tradeOutcomeRecorder.record`); D-588 |
| checklist `:504` | void by decision | as marked |
| PS-13 V1 drops the segment (W-264a hand-off) | fixed (W-280, D-566) | V1 passes the segment (`PortfolioService:472, 584, 644`); scenario 24, kept |

## 5a. Not covered

- **Scenario 2:** a bad seed cannot be sent over HTTP, because the seeder reads only the classpath
  (`ChargeSeederService:68 – 70`). `badseed/` belongs to its unit test.
- **Scenario 10:** two equally specific cards. Publishing over an open card of the same scope closes it, which
  `ChargesIntegrationTest.publishEndpoint_overAnOpenCard_closesTheIncumbent` already shows.
- **Scenario 13:** PER_UNIT and FORMULA. No shipped card uses either basis (the shipped bases are TURNOVER, FLAT,
  SCOPED_FLAT, DERIVED and SLAB), so `ChargeEngineTest` holds them.
- **Scenario 30:** a broker without a maintenance card. All three brokers ship one.
- **Probes:** none was run per kept test, as in W-264a.

## 5b. Fix rows placed (C3a, C4a: after W-264b, before W-264c, correctness first)

| Row | Gaps / entries |
|---|---|
| W-303 | G-2: exit load on the redemption value |
| W-304 | G-1: a trade no broker card covers is reported as priced |
| W-305 | G-3: an unreadable body answered with Jackson's text |
| W-306 | G-4: CLAUDE.md on which flows the charge engine prices |
| W-307 | CE-3: bound the resolver cache and give it a TTL before a second instance |
| W-308 | CE-4: `UserChargeService.record` safe under concurrent pricing of one transaction |
| W-309 | checklist `:457`: derive the charge summary from `user_charges` |

Q-2 is a question, not a row. CE-5 is closed here, and `docs/charges-engine/backlog.md` points to this report.

## 6. The failing scenarios, in full

These were run twice, failed the same way both times, and were not committed (C2a). They are written against
`AbstractChargeAuditIntegrationTest`.

### 6.1 G-2 — `tradeV2_whenAFundIsRedeemedInsideItsExitLoadWindow_chargesTheLoadOnTheRedemptionValue`

```java
// Given -- 60 units bought 10 Apr 2025 and 40 bought 10 Jun 2025, both at ₹100; 100 redeemed at ₹110 on 1 May 2026
tradeV2(fund("BUY", 60, 100, "2025-04-10"));
tradeV2(fund("BUY", 40, 100, "2025-06-10"));
tradeV2(fund("SELL", 100, 110, "2026-05-01"));
// When -- the SELL row of GET /user-charges/user/{email}
// Then -- the younger lot is held 325 days: 1 % of 40 × ₹110 = ₹44.00
assertMoney(44.0, amounts(sale).get("EXIT_LOAD"));
// fund(...) = trade("PARAGPARIKHFLEXICAP", …) with assetType MUTUAL_FUND and no segment
```

Both runs: `Expecting actual: 40.0 to be close to: 44.0`.

### 6.2 G-1 — `simulate_whenNoBrokerCardCoversTheDate_isNotReportedAsResolved`

```java
// Given -- the open Zerodha delivery card closed on 1 Jul 2026 with no successor
call(HttpMethod.PATCH, "/charge-schedules/ZERODHA_EQ_DELIVERY_2026_06/close?endDate=2026-07-01", admin(), null);
// When -- a delivery buy on 1 Aug 2026
JsonNode breakdown = json(simulate(simulation("DELIVERY", "BUY", 10, 1000, "2026-08-01")));
// Then
assertEquals("NO_SCHEDULE", breakdown.get("resolution").asString(), breakdown.toString());
```

Both runs: `expected: <NO_SCHEDULE> but was: <RESOLVED>`, with lines STT 10.0, SEBI_FEE 0.01, STAMP_DUTY 2.0,
GST 0.0 and `scheduleCode` `ZERODHA_MAINTENANCE_2025_04`.
