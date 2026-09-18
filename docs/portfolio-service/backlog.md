# PortfolioService — review backlog

Findings from a read of `portfolio/service/PortfolioService.java` (910 lines) at `55024ad`,
2026-09-18. Nothing here is fixed. Each entry says what it is, why it matters, what done looks
like, and — where the answer is not obvious — why it was not done at the time.

Numbered `PS-n` so as not to collide with the trade ledger's `B-n`
([`../trade-ledger/backlog.md`](../trade-ledger/backlog.md)) or the charges engine's `CE-n`
([`../charges-engine/backlog.md`](../charges-engine/backlog.md)). Format follows both.

**PS-1 through PS-7 are correctness.** PS-8 is a question for the owner. PS-9 onward is hygiene.

Suggested split when this is picked up: one branch for PS-1..PS-7, each with its failing test
first; a second, separate branch for PS-9..PS-15, which change no behaviour and should be
reviewable by reading the diff alone.

---

## PS-1 — Two of the three write paths skip validation entirely

**What.** `validateAssetRequest` is called from `addTransaction:93` and `addTransactionV2:135` and
nowhere else. `uploadTransactions` and `redriveTemporaryTransactions` both enter at
`processTransaction`, which calls only `sanitizeAssetRequest`. `AssetRequestParser` does no range
checking either — `setPrice:114` and `setQuantity:120` cast the cell value and assign it.

So a spreadsheet row carrying quantity `0`, a negative price, or a transaction date in 2030 is
accepted and writes a holding. Every guard the method documents — the positive-quantity rule, the
non-negative price, the future-date refusal, the email match — applies to one of three entry points.

**Why it matters.** The method's own Javadoc opens "Everything a trade must satisfy before it is
allowed to change a holding", and the paragraphs beneath it explain that a negative quantity is
*"not recoverable from the stored document alone"*. That reasoning is exactly as true on the bulk
path, which is the one that writes many rows at once and is therefore the one where an unrecoverable
write is hardest to spot.

The future-date guard has a second consequence specific to charges: a trade dated forward prices
against whichever rate card is open-ended today rather than the one in force when it settles. The
Javadoc says so. Upload bypasses it.

**Done looks like.** `validateAssetRequest` moves to the top of `processTransaction` — it is cheap
and idempotent, and that is the single choke point all three paths share. For upload specifically,
better is to validate per row into the existing `errors` list *before* anything is written, so a bad
row is named with its row number and the batch aborts whole rather than half-applied. Redrive needs
no special handling: a stored temp that fails validation becomes a `failed` entry through the
per-item catch, which is the right outcome for a request that should never have been stored.

---

## PS-2 — `validateAssetRequest` throws NPE on a null price

**What.** `PortfolioService.java:191` — `if (assetRequest.getPrice() < 0)` unboxes a `Double`
(`AssetRequest:49`). The quantity check on the line above is null-guarded; price is not.

**Why it matters.** `ControllerAdviser` maps `IllegalArgumentException` to 400 and everything else to
500. A request omitting `price` therefore gets a 500 from the method whose entire purpose is to
produce a 400 with a readable message. `getTotalValue` has the same exposure one call later.

**Done looks like.** `Double price = assetRequest.getPrice(); if (price == null || price < 0) throw
new BadRequestException(...)`. Note that zero must stay valid — bonus and split allotments are
issued free and the corporate-action flow depends on it, as the Javadoc already records.

---

## PS-3 — V1 buy records the order-execution time on the request, not the lot

**What.** `PortfolioService.java:353`, in the branch that merges into an existing same-day lot:

```java
assetRequest.getOrderTimeQuantities().add(orderTimeQuantity);   // line 353 — the request
```

The new-lot branch twelve lines down does it correctly:

```java
assetEntity.getOrderTimeQuantities().add(orderTimeQuantity);    // line 361 — the entity
```

`assetRequest` is discarded once the call returns, so on a second same-day buy the execution-time
record is dropped on the floor.

**Why it matters.** The lot's quantity grows but its `orderTimeQuantities` still describes only the
first buy, so the two disagree — a lot of 150 units whose order times account for 100. Anything
reading that field to reconstruct intraday sequence gets a silently incomplete answer, and the
shortfall is invisible in the stored document because nothing records what the total should be.

**Done looks like.** One character's worth of fix, plus a test that buys the same stock twice in one
day and asserts the saved lot's `orderTimeQuantities` sums to its quantity. V2 is unaffected — it
writes a lot per buy and never merges.

---

## PS-4 — The sell loop can run off the end of its lot list

**What.** Both allocation loops — `updateQuantityBySavingReportAndProfitAndLoss:591` (V1) and
`updateQuantityBySavingReportAndProfitAndLoss1:643` (V2) — are `while (sellQuantity > 0)` driving
`stockEntitiesIterator.next()` with no `hasNext()` guard. The only thing standing between that and a
`NoSuchElementException` is `validateTransaction`, which compares exactly:

```java
if (existingQuantity < assetRequest.getQuantity())
```

**Why it matters.** Quantities are `double` and fractional units are live — `getMutualFunds` and the
`MUTUAL_FUND` asset type are both in use. Three lots of `0.1` sum to `0.30000000000000004`. A sell of
`0.3` passes validation, consumes all three lots, and leaves `sellQuantity` at roughly `5.5e-17` —
positive, so the loop re-enters and `next()` throws. That surfaces as a 500 with no useful message,
*after* profit-and-loss rows and charge records have already been written for the lots consumed.

The converse is quieter and worse: a portfolio summing to `2.9999999999999996` rejects a legitimate
sell of `3.0` as "Not enough stocks to sell", and no amount of retrying will fix it.

This is the same class of defect as B-3/TL-8, and CLAUDE.md already states the rule — money is never
compared with a bare `==`. These two loops predate that rule being applied here.

**Done looks like.** `while (sellQuantity > EPSILON && iterator.hasNext())`, `validateTransaction`
comparing through `DoubleUtil`, and a test that sells a fractional total across three lots. Worth
checking at the same time whether the residue should be absorbed into the final lot rather than left
on the floor.

---

## PS-5 — V1 deletes emptied lots on exact double equality

**What.** `PortfolioService.java:457` — `asset -> asset.getQuantity() == 0`. V2 at `:436` already
does the right thing: `DoubleUtil.equal(0, asset.getQuantity())`.

**Why it matters.** Taken with PS-4, a fully-sold lot can be left holding `1e-17` units. It is not
deleted, so it stays in `assets` forever: it appears in holdings listings, contributes a row to
`combineAllDetailsOfEntities`, and will be picked up by the next sell's FIFO scan as an eligible lot
carrying a price. A zero-quantity lot has already caused one incident — see the `Infinity` entry at
the foot of the trade ledger backlog.

**Done looks like.** V1 uses `DoubleUtil.equal`, matching V2. This is one line and should be fixed
alongside PS-4, since PS-4 is what produces the residue it has to tolerate.

---

## PS-6 — Redrive's per-item catch does not isolate the item

**What.** `redriveTemporaryTransactions` is `@Transactional` and calls `processTransaction` in a
try/catch per item. `processTransaction` is a private method on the same class, so the call is a
self-invocation: no proxy, no second transaction. Every item runs inside the one transaction the
public method opened.

**Why it matters.** Catching the exception and continuing means the failed item's partial writes
commit alongside the successes. A temp transaction that records its `TransactionEntity` and then
fails in `sellStock` leaves the transaction row behind with no holding change to match it — and the
item is reported as `failed`, so nothing downstream knows to look for it.

The Javadoc is honest about this (*"the `@Transactional` annotation is a best-effort wrapper"*), but
the try/catch shape advertises per-item isolation that the code cannot deliver, which is how the next
person to read it draws the wrong conclusion.

**Done looks like.** Either drop `@Transactional` from the redrive method, so each
`processTransaction` carries its own boundary through the normal proxy path, or route the per-item
call through an injected collaborator annotated `REQUIRES_NEW`. The first is simpler and is probably
right: redrive is explicitly a resilient batch, and a single all-or-nothing transaction over an
unbounded list of temps is not what it wants anyway.

**Why it may not be urgent.** Multi-document transactions only engage when
`app.mongodb.transactions-enabled=true` (CLAUDE.md). Where that is off, each write already stands
alone and the behaviour is accidentally what PS-6 asks for. That makes this a correctness bug that
appears when the flag is turned on — which is a bad time to find it.

---

## PS-7 — No optimistic locking on the V1 buy merge

**What.** `AssetEntity` carries no `@Version`. `buyStock`'s merge branch is a read-modify-write:
read the lot, compute `newQuantity` and the weighted `newPrice`, save.

**Why it matters.** Two concurrent buys of the same stock, same broker, same day read the same
document, each computes a new total from the *same* starting quantity, and the second save wins. One
buy disappears from the holding while its `TransactionEntity` remains — so the ledger and the
portfolio disagree, and the transaction row is the one telling the truth.

**Done looks like.** `@Version` on `AssetEntity` and a retry on
`OptimisticLockingFailureException`, or the merge expressed as an atomic `$inc` through
`MongoTemplate`. Note the weighted-average price makes `$inc` alone insufficient; a version field is
the straightforward answer.

**Why not now.** V2 sidesteps it entirely by writing a lot per buy and never merging, so this is
V1-only. It stays open because V1 is live — CLAUDE.md is explicit that it "must not be treated as
dead code" — and because adding `@Version` to an entity with existing documents needs a moment's
thought about how those documents read back.

---

## PS-8 — V2 does not refuse a trade while temporary transactions are pending

**What.** `addTransaction:94` throws when `hasTemporaryTransactions` is true. `addTransactionV2` has
no equivalent check. `uploadTransactions:217` has one.

**Why it matters.** The V1 guard exists because a new trade applied ahead of a blocked one produces
the wrong lot ordering, and V2's FIFO sell over `transactionDate` is at least as sensitive to that as
V1's. V2 still calls `filterOutTransaction`, so it will create *new* temps correctly — it simply does
not stop you adding to the pile.

**This is a question, not a finding.** It may be deliberate: V2 writes a lot per buy, so the
consequence of ordering differs from V1's merge. Needs the owner's answer before either adding the
guard or writing down why V2 does not need one.

---

## PS-9 — The class does four jobs

**What.** 910 lines and twelve injected collaborators, covering the trade command path (V1 and V2
buy/sell, upload, redrive), portfolio queries (`getAllStocks`, `getAssets`, `getMutualFunds`,
`searchAssets`, date-range and export reads), Excel export, and a one-off data migration
(`updateTransactions`).

**Done looks like.** `PortfolioCommandService` and `PortfolioQueryService` along the seam that
already exists — the query half touches only `portfolioRepository`, `mongoTemplateService` and
`chargeViewAssembler`, so each class's constructor gets materially shorter. `updateTransactions`
goes to a maintenance component (see PS-10).

**Why not now.** It is the largest change on this list and touches nothing that is wrong. It should
follow PS-1..PS-7 rather than carry them, so that the correctness diff stays readable.

---

## PS-10 — Dead code, and a log line that prints its own expression

**What.** `updateTransactions:886`:

```java
log.info("Initiated update of all records of user: {}", "userMail.getEmail()");
```

The quotes make it log the literal text `userMail.getEmail()`. The method also `findAll()`s every
asset of every user into heap and `saveAll`s the lot, with no paging and no user scoping — it reads
as a migration that has already run.

Dead alongside it:

| Location | What |
|---|---|
| `:477` | `addTransactionInternal` — private, zero callers |
| `:701` | `double assetQuantity` — assigned, never read; the "pro-rate buy-side charges" comment above it describes work that moved to `LotChargeAllocator` |
| `:381` | `double totalValueOfTransaction` — used only by the commented-out line beneath it |
| `:77` | `// private final UserBrokerChargeService userBrokerChargeService;` |
| `:382`, `:653`, `:661` | three commented-out `assetEntity.setTotalValue(...)` |
| `:739` | `TradeOutcomeContext context = ...builder()...build(); return context;` — inline it |

**Done looks like.** All of it deleted, and a decision recorded on `updateTransactions`: if the
migration has run, it goes; if it has not, it is scoped and paged and moved out of this class.

---

## PS-11 — `combineAllDetailsOfEntities` divides by an unguarded total

**What.** `:571` — `assetResponse.setPrice(totalValue / totalQuantity)`, where `totalQuantity` is
accumulated from the group's lots.

**Why it matters.** A group in which every lot has zero quantity yields `NaN`, which is not
representable in JSON and serialises as `null` or throws depending on the mapper's configuration.
PS-5 is exactly the mechanism that leaves zero-quantity lots in the collection, and the trade ledger
backlog already records an incident where an unguarded divide by a lot quantity wrote `Infinity`
into a stored total permanently.

**Done looks like.** Guard the divide and decide what a zero-quantity group's price should read as —
most likely the group is filtered out before it reaches here.

---

## PS-12 — Naming and visibility

- `updateQuantityBySavingReportAndProfitAndLoss1` — the trailing `1`. It is the V2 sell allocation;
  name it that.
- It is also `public` with exactly one caller (`sellStockV2`), as is
  `updateBrokerChargesAndProfitAndLoss` (V1 buy only). Both should be private. `buyStock`,
  `buyStockV2`, `sellStock` and `sellStockV2` are public and called only from within this class too
  — worth checking whether tests depend on that before narrowing them.
- `stockWithCodeAndBroker` and `stockCodeWithBroker` are stateless instance methods returning a
  `Function`; they allocate a lambda per call and should be static constants.

---

## PS-13 — The V1 buy discards the trade's segment

**What.** `buyContext:416` (V2) passes `assetRequest.getSegment()`.
`updateBrokerChargesAndProfitAndLoss:631` (V1) uses the 13-argument `ProfitLossContext` overload,
which defaults `segment` to `DELIVERY`.

That overload is deliberate and documented — *"Every trade recorded before Chunk 10b was delivery —
there was no segment concept anywhere in the application"*. The point is that the premise has since
stopped being true: `AssetRequest:61` now carries a `segment` field, so a V1 caller can submit
`INTRADAY` and have it silently recorded as delivery.

**Why it matters.** Segment is one of the axes `ChargeScheduleResolver` selects a rate card on. A
V1 intraday trade priced as delivery draws the wrong card. Latent while V1 charge recording is
what it is, but it is a wrong value written to a stored document, not a display issue.

**Done looks like.** V1 passes `assetRequest.getSegment()` like V2 does, and the overload's Javadoc
gains a line narrowing it to the corporate-action call sites that genuinely have no segment.

---

## PS-14 — Grouping keys concatenate without a delimiter

**What.** `stockWithCodeAndBroker` returns `asset.getStockCode() + asset.getBrokerName().name()`,
and `stockCodeWithBroker` the same over `AssetResponse`. Both are used as map keys in
`groupedWithCharges` and `getAssets`.

**Why it matters.** Unseparated concatenation collides: stock `AB` at broker `CDEF` keys identically
to stock `ABC` at broker `DEF`. Two holdings would merge into one row with a weighted-average price
across both. Whether any real NSE code and `BrokerName` pair can collide depends on the enum's
values, so this may be unreachable today — and it is unreachable by luck rather than by design.

**Done looks like.** A delimiter that cannot occur in a stock code, or a small record key. The
record is preferable: it makes the collision impossible rather than unlikely, and reads better at
both use sites.

---

## PS-15 — Small things

- **`buildRedriveMessage`** carries four nested "is anything before me" checks to place its commas.
  A list of label/count pairs, filtered non-empty and joined with `", "`, is three lines and cannot
  drift as categories are added.
- **Redrive checks the corporate-action filter twice per item** — explicitly via
  `filterOutTransaction(userMail, assetRequest, true)`, then again inside `processTransaction`. Two
  reads per temp where one would do. Harmless, and the second call is what populates `itemFiltered`,
  so removing the first needs a moment's care.
- **No blank line between `package` and the first import** (line 1 to line 2). `spotless:apply`
  moves it; the file has presumably not been through it since.
