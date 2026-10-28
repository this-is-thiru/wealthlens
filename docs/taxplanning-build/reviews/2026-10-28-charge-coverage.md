# Charge coverage — what the app accepts against what the brokers charge (2026-10-28)

**Asked by the owner** (session 37, during the W-163b review): "do research and check everything covered". Placed
as W-177 – W-179, after W-163c (owner: "yes, write it and place after W-163c"; D-383).

**Method.**
- **What the app accepts:** read from the enums.
  - `BrokerName`: `UPSTOX`, `FYERS`, `ZERODHA`.
  - `AssetType`: `EQUITY`, `MUTUAL_FUND`, `BOND`, `GOLD_BOND`, `FD`, `INSURANCE`.
  - `TradeSegment`: `DELIVERY`, `INTRADAY`, `FUTURES`, `OPTIONS`, `NA`.

  Every V2 trade reaches the engine (`PortfolioService:379`, `:668`; `ProfitAndLossService:520`), so a trade with no
  matching card is recorded `NO_SCHEDULE` and charged nothing.
- **What the brokers charge:** the shipped cards (after W-163b) against each broker's page, read 2026-10-28: zerodha.com/charges,
  upstox.com/brokerage-charges, fyers.in/pricing and fyers.in/charges-list.

**Caveat:** the pages were read through WebFetch, which returns a model's summary of the page, not its raw text.
Every figure below is to be re-read on the page before it goes on a card, as AC-2 did.

## Matches (no action)

- **Rates already on the cards:**
  - Zerodha delivery and intraday.
  - Upstox delivery: brokerage ₹20 per order, DP ₹20 per scrip on sell.
  - Fyers delivery: "₹20 or 0.3% per executed order … whichever is lower", DP "₹12.5 + GST per scrip [₹3.5 (CDSL
    Fee) + ₹9 (FYERS Fee)]".
  - Zerodha DP: ₹15.34 including GST, which is ₹3.50 CDSL + ₹9.50 Zerodha, the 2026-06-19 card's ₹13.00.
  - Zerodha direct mutual funds: "₹ 0 commissions & DP charges".
- **The law's charges, the same on all three pages:**
  - STT: 0.1 % on delivery buy and sell; 0.025 % on intraday sell.
  - Stamp duty: 0.015 % (delivery) and 0.003 % (intraday) on buy.
  - SEBI fee: ₹10 per crore.
  - GST: 18 %.

  NSE's transaction charge is printed as 0.00307 %; the cards carry 0.0030699 % from NSE's circular.
- **IPFT:** the pages print ₹0.01 per crore; the cards carry ₹10 per crore. That is the owner's decision of 2026-09-08,
  recorded in the card notes, not a new finding.

## Trades the app accepts with no card (they record `NO_SCHEDULE`)

| # | Gap | What the pages say | Row |
|---|---|---|---|
| G1 | Upstox and Fyers equity intraday | Upstox: "₹20 per executed order or 0.1% (whichever is lower)". Fyers: "₹20 or 0.03% per executed order for Intraday (whichever is lower)". Statutory charges are as W-163b's intraday card | W-177, **done** (D-392) |
| G2 | Upstox and Fyers mutual funds | Upstox: "Zero Commission on Mutual Funds and IPOs". Fyers: "₹0 brokerage on mutual funds and IPOs" | W-177, **done** (D-392) |
| G3 | Upstox and Fyers AMC (maintenance cards) | Upstox: ₹300 + GST for non-BSDA accounts, ₹0 in the first year for customers onboarded from 14 Feb 2025, BSDA "refer to the help center". Fyers: AMC "₹ 0" | W-177, **done** (D-392) |
| G4 | Equity futures and options, all three brokers | Broker rates: Zerodha futures 0.03 % or ₹20, options flat ₹20; Upstox futures 0.05 % or ₹20, options ₹20; Fyers futures 0.03 % or ₹20, options ₹20. The pages also show a **change in law from 1 Apr 2026**: futures STT 0.02 % → 0.05 % on sell; options 0.1 % → 0.15 % of premium on sell, plus 0.15 % on exercised options (Upstox's page, with dates). Futures stamp duty is 0.002 %, options 0.003 %; options transaction charges are on premium. These need statutory cards with the law's dates, sourced as W-163c | W-178 |
| G5 | `BOND` and `GOLD_BOND` traded through a broker | No page states a charge for bonds, sovereign gold bonds or government securities (Zerodha mentions only a ₹0.25 CDSL discount on bond debits) | W-179 |

## Existing cards simpler than the pages

| # | Finding | Row |
|---|---|---|
| S1 | **BSE trades priced at NSE's rate.** Zerodha and Fyers print BSE at 0.00375 %, against NSE's 0.00307 %, for delivery and intraday. The cards name no exchange (`exchange: null`), so a BSE trade is charged NSE's rate. The card notes say "a card naming an exchange would outrank this one" | W-179 |
| S2 | **Zerodha AMC is a flat ₹300.** The page has: the first year free on new resident individual accounts; BSDA ₹0 (holdings up to ₹4 lakh), ₹100 (₹4 – 10 lakh), ₹300 (above); non-BSDA ₹300; all + 18 % GST, charged quarterly | W-179 |
| S3 | **Brokerage by account type and plan.** Zerodha charges companies, partnerships, trusts and **HUFs** 0.1 % or ₹20 on delivery, and the brief names HUF as the second assessee. NRIs: 0.5 % or ₹50 (non-PIS) and 0.5 % or ₹200 (PIS). Fyers Prime is ₹15 per order. The cards model one plan, and `planCode` exists but is unused | W-179 |
| S4 | **Corporate-action orders** (buyback, OFS, takeover, delisting) cost ₹20 + GST at Zerodha and Upstox. The engine exempts corporate actions unless a rule opts in | W-179 |

## Not trade charges (no row)

Pledge, call & trade, auto square-off, margin-trading interest, off-market transfer, physical statements and payment
gateway fees are account services. The engine prices trades and the AMC cycle, and the catalogue has no code for
these. They are recorded here so the omission is known, not placed.

## Rows

- **W-177:** the missing broker cards (G1 – G3). Each figure is re-read on the page; W-163b's statutory cards are
  reused. **Done (D-392):** eight cards, the pages READ raw (`sources/2026-10-28-broker-charge-pages.md`). Since
  then an uncarded Upstox or Fyers trade (G4, G5) resolves `NO_MATCHING_RULES` against the broker's unscoped
  maintenance card, not `NO_SCHEDULE`. It is still ₹0 and still a gap.
- **W-178:** equity futures and options (G4). Broker cards, plus statutory cards windowed by the law. The STT change
  of 1 Apr 2026 is sourced to its Act, as W-163c does.
- **W-179:** decisions for the owner: bonds and gold bonds (G5), BSE cards (S1), Zerodha AMC tiers (S2), account-type
  and plan brokerage (S3), corporate-action order charges (S4). Each decision is placed as its own row once made.
