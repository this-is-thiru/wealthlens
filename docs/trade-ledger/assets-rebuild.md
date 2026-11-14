# Rebuilding `assets` from `transactions` — design

*W-283a (D-577); built by W-283a2 after W-283a1 records the flow (D-578). Trade-ledger backlog B-4's other half;
W-283 rebuilt `profit_and_loss` (D-575).*

## What a rebuild produces

A rebuild produces one user's lots (`assets`) as the live trade paths would have left them, had every trade and every
performed corporate action been applied in date order. It reads stored rows and writes nothing else. It never
performs a corporate action, never prices a trade again (ADR-32), and never touches `transactions`.

A dry run comes first. A real run replaces the user's lots, the same report-then-replace shape as W-283.

## Inputs

| Input | Used for |
|---|---|
| `transactions` of the user, `status` not `TEMPORARY` | buys, sales and bonus allotments, in order |
| `trade_flow` on each transaction (recorded from W-283a1's deploy, D-580; absent before it and on a bonus) | lot shape (below) |
| the user's stored `assets` | lot shape and charges for a transaction with no `trade_flow` (C1a) |
| `corporate_actions` of type `STOCK_SPLIT` or `DEMERGER` | applied between trades (C2a) |
| `last_performed_corporate_actions` of the user | which of those were performed |
| `user_charges` of a buy | a V2 lot's charges |

A `TEMPORARY` transaction was never applied. It waits for a corporate action, and its redrive is its own transaction
(`sourceTempTransactionId`), so the replay skips the temporary.

## The order of events

All events are sorted by date. On one date, the corporate actions come first, then the trades in stored order.

- **Why corporate actions first.**
  - A trade is held while an unperformed split, bonus or demerger has a record date between its quarter's start and
    its own date, both ends included. See `TemporaryTransactionService.anyCorporateActionToPerform` (`:76 – 79`) and
    `CorporateActionRepository:17 – 18` (`$gte`, `$lte`).
  - So a trade dated on a record date is applied live only after that action.
  - The action itself touches only lots dated strictly before its record date:
    `findByEmailAndStockCodeAndTransactionDateBefore` (`PortfolioRepository:29`, `PortfolioService:742 – 743`).
- **Trades on one date** keep the order they were stored in.
  - A sale draws lots by `transaction_date` alone (`findEligibleHoldingsForSell`, `PortfolioRepository:21 – 25`), so
    same-day lots are ordered by when they were created.
  - The rebuild makes that order explicit: the lot's first buy transaction, in stored order.

## Lot shape (C1a)

| The buy's `trade_flow` | Lot |
|---|---|
| `V2` | its own lot (`buyStockV2`) |
| `V1` | merged into the lot with the same email, stock, broker, account holder, segment (W-296, D-598) and date if one exists, else its own. A merge uses a weighted-average price and adds the charges and order time (`PortfolioService:372 – 420`) |
| absent (stored before W-283a1) | read from the stored lots. Buys listed together in one stored lot's `buy_transaction_ids` form one merged lot, as V1. A buy listed alone is its own lot. A buy no stored lot lists is its own lot and is **listed in the report** |

A buy that no stored lot lists was usually fully sold, since a lot with nothing left is deleted (`hasNothingLeft`,
`PortfolioService:486 – 491`). For a fully sold buy, shape changes nothing that remains. It matters only for a
partly sold one, and a partly sold lot is still stored.

**Charges on a lot:**
- **`V1`:** the transaction's entered `broker_charges` and `misc_charges`, summed on a merge.
- **`V2`:** the buy's `user_charges` total when the row has a figure (`applyComputedCostBasis`,
  `PortfolioService:458 – 464`, with `app.charges.authoritative` on). Otherwise the entered figure.
- **Absent:** the stored lot's charges when it is found. Otherwise the entered figure, listed.

**Bonus:** a bonus allotment is a stored BUY at price 0, dated on the allotment date, marked
`corporate_action_type: BONUS` (`CorporateActionService:317 – 345`). It is its own lot, `corporate_action_type`
`BONUS`, whatever its flow, as live (`:297 – 304`).

## Corporate actions (C2a)

- **Applied:** a split or demerger with record date R, for a stock, type and broker, is applied when the user's
  last-performed date for that stock, asset type, action type and broker is on or after R.
  - That is the live test, `isCorporateActionToPerform` (`TemporaryTransactionService:89 – 102`), read the other way
    round.
  - Actions are performed quarter by quarter (`performPendingCorporateActions`), so every action up to the
    last-performed date was performed.
- **Not applied:** an action after that date is pending. The rebuild leaves it pending and lists it.
- **Split:** each lot dated before R, for that stock and broker, gets `quantity × new ÷ old` and `price × old ÷ new`
  (`SplitRatio:32 – 39`). Date and charges are unchanged (D-438).
- **Demerger:** each lot dated before R is repriced and renamed to the main stock, and gets one new lot per demerged
  stock, with the parent's date and holder (`CorporateActionService:416 – 488`). The same code makes both, so the
  replay calls it rather than copying its arithmetic.
- **Name or symbol change:** nothing to do. It rewrote `transactions` too (`:227 – 251`), so the replay already sees
  the new code.
- **Each lot's `corporate_actions` list:** the actions recorded on its buy transactions, then each split or demerger
  the replay applied to it.

## Sales

A sale draws the lots of its stock, broker and account holder dated on or before it, oldest first (above). Each lot
gives what it holds until the sale's quantity is met. This is the FIFO both flows share since W-271. Each lot drawn
records the sale in `sell_transaction_ids`, and a lot with nothing left is removed.

**Every sale records its share of a lot's buy charges** in `allocated_buy_charges` and `allocated_buy_misc_charges`,
through `LotChargeAllocator`. V1 does it in `allocateSale` (`PortfolioService:549 – 550`), and V2 in
`TradeOutcomeRecorder.rowFigures` (`:166 – 167`) on the lots `sellStockV2` then saves. So a sale needs no flow.

*Erratum (W-293, D-588):* W-283a2 first held that a V2 sale left those untouched, and read an older sale's flow from
its `trade_outcomes` rows (D-582 C2a). Both were wrong and are removed.

A sale that finds less than its quantity is refused, naming the transaction and the shortfall. That is what a
rebuild exists to surface.

## Lot ids, and the read-only first step

- **W-283a2** compares only, and writes nothing.
- **W-283a3** writes (`dryRun=false`, D-586). In one transaction it deletes the user's lots and saves the rebuilt ones.
  - A rebuilt lot paired with a stored one keeps that lot's id, so `trade_outcomes.source_buy_lot_id` keeps pointing
    at it (C3a, D-582).
  - It also keeps the stored lot's order times, its list of corporate actions and its creation (C2a, D-586). Its
    figures are the replay's.
  - Any other lot gets a new id and is listed (`newLots`).
  - It writes even when the replay listed readings it could not confirm (C1a), so read the dry run first (runbook
    §2.12).
- The comparison matches lots by stock, broker, account holder, date and buy transactions, not by id. A demerged lot
  lists no buy, so two demerged from different parents on one date share a key and are compared in order.

## Copied as live, not compared

The replay builds what the live paths build, even where that looks wrong, so a difference in the report means drift.
It never means a fix.

- **A bonus lot** is a copy of the first lot held. Since W-291 (D-589) both live and the replay reset it to an
  allotment: nil charges and allocated charges, no sales, and only the bonus among its actions
  (`CorporateActionService.asAllotment`).
- **Segment:** a lot takes its transaction's segment, as `AssetRequest.asAsset` does (W-292, D-591). Lots and
  transactions stored before W-292 say `DELIVERY` whatever was traded; W-297 corrects the transactions, and a rebuild
  then carries the correction to the lots.
- **Not compared:**
  - a lot's order times, which a V2 lot takes from the request and a transaction does not keep;
  - its list of corporate actions. W-283a3 keeps both from the matched stored lot.

## The report

The report has one entry per lot, giving the stored figures against the rebuilt ones: quantity, price, broker and
misc charges, both allocated charges, and the corporate-action type. Each entry names the fields that differ. It
also lists:
- every buy whose shape was read from a stored lot, or not found;
- every corporate action applied;
- every pending action left;
- every temporary skipped.

## What is refused

The whole call is refused, naming the cause, when:
- a sale draws more than the lots hold;
- a split ratio does not parse (`SplitRatio.parse`);
- a demerger names other than one demerged stock (as live, `:428 – 430`);
- a stored lot lists a buy that is not a stored transaction.

## Worked example (V2, one broker and holder; by hand)

Split 2:1 (new : old), record date 10 Feb 2026, performed for the user (last-performed date ≥ 10 Feb).

| Date | Event | Lots after (quantity @ price) |
|---|---|---|
| 5 Jan 2026 | buy 10 @ ₹100 | A 10 @ 100 |
| 20 Jan | buy 6 @ ₹110 | A 10 @ 100; B 6 @ 110 |
| 1 Feb | sell 4 | A 6 @ 100; B 6 @ 110 |
| 10 Feb | split 2:1 (lots dated before 10 Feb) | A 12 @ 50; B 12 @ 55 |
| 10 Feb | buy 5 @ ₹60 (dated R, so not split) | A 12 @ 50; B 12 @ 55; C 5 @ 60 |
| 15 Feb | sell 15 | A gone (12); B 9 @ 55 (3); C 5 @ 60 |

**Check:** (10 + 6 − 4) × 2 + 5 − 15 = 14 = 9 + 5.

**The tie-break matters.** Suppose a **sale** of 4 is dated on 10 Feb instead of the buy.

| Order on 10 Feb | A after | B after | Held |
|---|---|---|---|
| split, then sale (this design; live, since the sale is held) | 8 @ 50 | 12 @ 55 | 20 = (6 + 6) × 2 − 4 |
| sale, then split | (6 − 4) × 2 = 4 @ 50 | 12 @ 55 | 16 |

The second order loses four shares. The golden test for W-283a2 is the first table and this variant.

## A live gap found writing this (W-289)

A trade is held only for an action recorded **in its own quarter** (`quarterStart`, `:77`). Take a split recorded
20 Mar that is not yet performed, and a sale dated 5 Apr. The sale is not held: it draws pre-split units, and the
later split multiplies what is left. For example:

- holding 10, split 2:1 on 20 Mar, sell 4 on 5 Apr;
- live: (10 − 4) × 2 = 12;
- correct, and this design: 10 × 2 − 4 = 16.

The rebuild follows record-date order, so its dry run shows such a user's difference. **Closed by W-289 (D-587):** the trade is now held when such an action reaches a lot the user holds with that broker; runbook §3.16 counts the users who may already be affected.
