# Corporate actions — gap audit (W-264c, 2026-11-13)

Driven through the HTTP API on the Testcontainers harness (C1a). Zerodha equity delivery lots bought through V2.
Each expected value was written in `plans/W-264c.md` before the first run, and each failing scenario was run twice
with the same result. Passing scenarios that add coverage are kept in `CorporateActionGapAuditIntegrationTest`
(7 tests, C2a). Failing ones are quoted in §4, not committed.

## 1. Results

| # | Scenario | Expected | Got | |
|---|---|---|---|---|
| 1 | Bonus 1:1; Self 10, Mother 6 at one broker | Self 20, Mother 12 | Self **26**, Mother 6 | **G-1** |
| 2 | Bonus 1:2 on 3 | 4 | 4 | ok |
| 3 | Split 2:1 on 10 @ ₹500 | 20 @ ₹250, ₹5,000 | same | ok |
| 4, 12 | 5 bought before the record date, 7 on it; then a held buy of 3; perform; redrive | 20 | 20 | ok: W-270 / W-271 hold (C3a) |
| 5 | Demerger 1:1, cost 80:20, 10 @ ₹1,000 | A ₹8,000, B 10 / ₹2,000 | same | ok |
| 6 | ITC's demerger: 1,000 ITC @ ₹400, 1:10, 86.49:13.51 | ITC Hotels **100**, ₹54,040 | **10,000** | **G-2** |
| 7 | Sell 5 of the demerged B | 200, 5 left | same | ok |
| 8 | Split (record 10 Jan) and bonus (20 Jan), bonus entered first; 10 @ ₹100 | 40 | **30** | **G-3** |
| 9 | Perform the same quarter twice | unchanged | unchanged | ok |
| 10 | Perform with month "april" | 400 naming the four months | 400 "No enum constant java.time.Month.april" | **G-4** |
| 11 | Rights issue on SBIN and bonus on WIPRO in one quarter; a held SBIN buy | 200; WIPRO bonused; buy redriven | **400 "Invalid action typeRIGHTS_ISSUANCE"** | **G-5** |
| 15 | `add`, `perform`, `update/priority` with a USER token | 403 | **200** | **G-6** |
| 16 | A USER token on another user's `perform` and `stocks/all` | refused | refused, 401 | noted (§3) |
| 13, 14 | — | — | not run: 13's premise cannot arise through the API (a trade dated before the record date is never held, `TemporaryTransactionService:78 – 90`); 14 needs a forced failure no request causes | — |

## 2. Gaps

**G-1. A bonus pools every holder at a broker into the first lot's holder.** `processBonusShares`
(`CorporateActionService:280 – 320`) sums all of the broker's lots into one count and writes **one** bonus lot copied
from the first lot. Self ended with 26 and Mother with 6. *Effect:* wrong holdings per account holder, and so wrong
gains per person. *Fix row:* W-312.

**G-2. A demerger reads its share ratio backwards.** The market writes ratios as **new:held**: ITC was "1:10", one
ITC Hotels share for every 10 ITC (ITC's investor page; the api-collection's Motherson Sumi Wiring bonus `1:2` uses
the same order, and the bonus code reads it so). `demergeLots` multiplies the held quantity by the second number
(`:466`, `asNewEntity` `:525`) and never uses the first (`:465`). It made 10,000 ITC Hotels at ₹54.04: a cost of
₹5,40,400 instead of ₹54,040, so ₹8,85,960 in total from ₹4,00,000. `1:1` hides it, and every test and example uses
`1:1`. *Fix row:* W-313.

**G-3. Actions in one quarter run latest record date first.** `findByTypeInAndRecordDateBetween` sorts by
`record_date` descending (`CorporateActionRepository:23`; corrected by W-314's plan, the first draft said "sorts
nothing"). The bonus lot is dated its allotment (22 Jan), so a split
recorded 10 Jan, run after it, skips that lot: 30 shares, not 40. *Fix row:* W-314.

**G-4. A wrong month is answered in Java's words.** `Month.valueOf` (`:138`) answers "No enum constant
java.time.Month.april". The same kind of gap as W-302 and W-305. *Fix row:* W-315.

**G-5. A rights issue or buyback blocks trades but can never be performed.** `FILTERABLE_CORPORATE_ACTIONS` lists
`RIGHTS_ISSUANCE` and `BUYBACK` (`CorporateActionType:20`). Trades are held for them, and the perform loop fetches
them, but its switch throws (`:198 – 202`). The quarter's run stops at that action: WIPRO's later bonus was not
applied, and the held SBIN buy can never be redriven. `CLAUDE.md` names only the three types that are processed.
*Fix row:* W-316.

**G-6. Any signed-in user can add, reprioritise and globally perform corporate actions.** Only `delete` carries
`@PreAuthorize("hasAnyRole('SUPER_USER')")` (`CorporateActionController:61`). `add` (`:30`), `update/priority`
(`:42`), `perform` (`:67`, a name change rewrites every user's lots and transactions) and `perform/test` (`:74`) do
not. A user can enter a false bonus or demerger that every user's next perform applies. *Fix row:* W-317.

## 3. Noted, no row

- **Another user's path answers 401, not 403** (scenario 16). The request is refused, so no data is reached. 401
  says "not signed in" to a caller who is, and 403 would be the accurate status. This is wording, not access.

## 4. The failing scenarios (as run, twice each)

- `perform_whenTwoHoldersHoldTheStockAtOneBroker_givesEachHolderTheirOwnBonus`: Self "expected: 20.0 but was: 26.0".
- `perform_whenADemergerIsOneForTen_allotsOneNewShareForEveryTenHeld`: "expected: 100.0 but was: 10000.0".
- `perform_whenASplitAndABonusFallInOneQuarter_appliesThemByRecordDate`: "expected: 40.0 but was: 30.0".
- `perform_whenTheMonthIsNotAQuarterStart_answers400NamingTheMonths`: the body was "No enum constant
  java.time.Month.april".
- `perform_whenARightsIssueIsInTheQuarter_neitherStopsTheQuarterNorHoldsATradeForever`: 400 "Invalid action
  typeRIGHTS_ISSUANCE".
- `adminRoutes_whenTheCallerIsNotASuperUser_answer403`: `add` "expected: 403 but was: 200".

Each fix row's plan starts from its scenario here, as its red test.

## 5. Placing (C4a)

Each gap above gives a wrong holding (G-1, G-2, G-3, G-5) or lets any user change every user's holdings (G-6). The
owner's instruction "fix to best" (2026-11-13) rules them into Phase 10, after W-264c and before W-264d, with
correctness first: W-317 (G-6) first, then W-313, W-312, W-316, W-314, W-315.
