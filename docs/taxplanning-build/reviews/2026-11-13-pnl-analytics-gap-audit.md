# P&L, reports and analytics — gap audit (W-264d, 2026-11-13)

Driven through the HTTP API on the Testcontainers harness (C1a), with Zerodha equity delivery trades through V2 and ₹0
charges. Every expected value was written in `plans/W-264d.md` before the first run, and every failing scenario was
run twice with the same result. Six passing scenarios are kept in `ProfitAndLossGapAuditIntegrationTest` (C2a).
Portfolio has no PDF or Excel report of its own; held assets and the tax PDF are tax planning's.

## 1. Results

| # | Scenario | Expected | Got | |
|---|---|---|---|---|
| 1 | V2: 10 @ ₹100, sell 4 @ ₹150 on 5 Mar 2026 | ₹200 for the year, in March's first half | same | ok |
| 2 | Sales on 31 Mar and 1 Apr 2026 | ₹50 in `2025-2026`, ₹50 in `2026-2027` | same | ok |
| 3 | Sales on 15 and 16 Mar | ₹50 per half | same | ok |
| 4 | Scenario 1 through V1 | ₹200 | ₹200 | ok: V1 and V2 agree |
| 5 | P&L for `2025-26` | 400 naming `2025-2026` | **200, an empty P&L** | **G-4** |
| 6 | Summary with one buy held `TEMPORARY` | `totalTrades` 2 | **3** | **G-1** |
| 7 | Allocation after scenario 1 | EQUITY ₹600, 100 % | same | ok |
| 8 | One win, no losses | no ratio | **`winLossRatio` 0.0** | **G-5** |
| 9 | XIRR: ₹1,000 out 1 Jan 2025, ₹1,100 back 1 Jan 2026 | 10 % | 10 % | ok |
| 10 | Scenario 9 plus a held buy | 2 cash flows, 10 % | **3 cash flows** | **G-1** |
| 11 | TCS and INFY held, a price for TCS only | INFY named as left out | **−27.4 %, no message, `includesOpenPositions` true** | **G-2** |
| 12 | TCS priced twice | 400 naming TCS | **500 "Duplicate key TCS (attempted merging values 110.0 and 120.0)"** | **G-3** |
| 13 | `POST /request`, `/request2` | not served | **200: echoes the body; searches assets** | **G-6** |

## 2. Gaps

**G-1. Analytics count trades held `TEMPORARY`.** `AnalyticsService` reads every transaction (`:48`, `:199`). A held
buy becomes an XIRR cash flow and a summary trade before it is recorded, or even though it may never be. *Wrong
figure:* XIRR and `totalTrades`. → **W-319**.

**G-2. XIRR treats an unpriced holding as worth nothing.** For a holding missing from `currentPrices`, the money paid
stays in the cash flows but its value is left out (`:211 – 222`). With TCS and INFY each costing ₹1,000 and a price
for TCS only, the answer is −27.4 %, with no message and `includesOpenPositions` true. *Wrong figure, presented as
complete.* → **W-318**.

**G-3. Two prices for one stock answer 500 in Java's words.** `Collectors.toMap` (`:211`). → **W-321**.

**G-4. A financial year written differently reads as empty.** The P&L stores `2025-2026`
(`FinancialYear:13`), and `getProfitAndLoss` looks it up as given (`ProfitAndLossService:239 – 245`). `2025-26`, the
form tax planning uses, answers 200 with every figure null. `FinancialYear.parse` already refuses a bad label with a
clear message. *A silent empty answer.* → **W-320**.

**G-5. Wins with no losses show a win/loss ratio of 0.0** (`:137`), which reads as "no wins". → **W-322**.

**G-6. Two test routes ship.** `/portfolio/user/{email}/request` echoes an `AssetRequest`. `/request2` runs caller
query filters over the user's assets (`PortfolioController:101 – 113`, "for testing purpose only"). Same kind of fix
as `TestController` → **W-311 widened**.

## 3. The failing scenarios (as run, twice each)

Quoted with their values in §1. Each fix row's plan starts from its scenario here: `profitAndLoss_whenTheYearIsNot
WrittenAsStored_answers400NamingTheForm`, `summaryAndAllocation_whenATradeIsHeld_countOnlyRecordedTrades`,
`performanceMetrics_whenThereAreWinsAndNoLosses_giveNoWinLossRatio`, `xirr_whenATradeIsHeld_leavesItOut`,
`xirr_whenAHoldingHasNoPrice_namesIt`, `xirr_whenAStockIsPricedTwice_answers400NamingIt`, `testRoutes_areNotServed`
(in `plans/W-264d.md`'s scenario table).

## 4. Placing (C3a)

- **Named for the owner's ruling, because each gives a wrong or silently empty figure:** W-318 (G-2), W-319 (G-1),
  W-320 (G-4).
- **After Phase 10** (D-622): W-321 (G-3), W-322 (G-5), and W-311 widened (G-6).
- All five are small enough to batch: W-318 + W-319 + W-321 + W-322 all change `AnalyticsService`, and W-320 changes
  one read.

**Ruling (2026-11-13):** the owner ruled W-318 – W-322 into Phase 10 as one batch, after W-264d.
