# Broker charge pages — Upstox and Fyers intraday, mutual funds and AMC (W-177, 2026-10-28)

The eight cards W-177 adds cite this record (`plans/W-177.md`; D-383 G1 – G3). **READ** means this session fetched
the page with `curl` (UA "Mozilla/5.0", HTTP 200), removed `<script>` and `<style>`, took the text between tags,
and quoted it below. No summary sits between the page and these words. That is the difference from the coverage
review, which read the pages through WebFetch.

## The figures

| # | Card rule | On the card | Page | Status | Words on the page |
|---|---|---|---|---|---|
| 1 | `UPSTOX_EQ_INTRADAY_2025_04` / `_2026_03` `BROKERAGE` | MIN(0.1 %, ₹20) | upstox.com/brokerage-charges/ | READ | *"Brokerage \| Equity Delivery \| ₹20 \| per executed order \| Equity Intraday \| ₹20 per executed order or 0.1% (whichever is lower)"* |
| 2 | `UPSTOX_EQ_INTRADAY_2025_04` `EXCHANGE_TXN` | 0.00297 % | same | READ | *"Transaction charges (From 1st October 2024 to 28 February 2026) \| NSE: 0.00297% per trade on buy & sell."*, printed alike for Equity Delivery and Equity Intraday. The 2026-03 card's 0.0030699 % is the delivery cards' figure from NSE's circular of 2026-02-27, which the page does not print |
| 3 | `UPSTOX_MF_2025_04` `BROKERAGE` | ₹0 | same | READ | *"Zero Commission on Mutual Funds and IPOs"*; the header: *"₹ \| 0 \| Brokerage \| * \| On Mutual Funds and IPOs"* |
| 4 | `UPSTOX_MAINTENANCE_2025_04` `AMC` | ₹300 a cycle | same | READ | *"2. AMC \| ZERO AMC is applicable for newly onboarded customers in the first year \| Non-BSDA users: \| AMC of ₹ 300 + GST \| BSDA Users: \| Know more"*; footnote: *"Annual Maintenance charges are free upto one year for users onboarded from 14-Feb-2025 onwards"*. The waiver and the BSDA tier are not modelled (C1a; W-179 S2) |
| 5 | `FYERS_EQ_INTRADAY_2025_04` / `_2026_03` `BROKERAGE` | MIN(0.03 %, ₹20) | fyers.in/charges-list | READ | *"₹20 or 0.03% per executed order for Intraday (whichever is lower)."* fyers.in/pricing/ FAQ: *"Intraday: ₹20 or 0.03% per executed order, whichever is lower."* That page's table also shows *"₹15 \| Or 0.03% per executed order"*, the Prime plan (W-179 S3) |
| 6 | `FYERS_MF_2025_04` `BROKERAGE` | ₹0 | fyers.in/charges-list | READ | *"Mutual Funds & IPO \| Zero brokerage for Mutual Funds & IPO."* fyers.in/pricing/: *"FYERS offers ₹0 brokerage on mutual funds and IPOs."* |
| 7 | `FYERS_MAINTENANCE_2025_04` `AMC` | ₹0 | fyers.in/charges-list | READ | *"Demat Annual Maintenance Charges (AMC) \| ₹ 0"*. fyers.in/pricing/: *"FYERS charges ₹0 for account opening and ₹0 annual maintenance charges (AMC) for trading and demat accounts."* |

The STT, stamp duty, SEBI fee and GST these trades also carry come from the statutory cards
(`sources/2026-10-28-statutory-charge-rates.md`). The IPFT and the Fyers 2025-04 exchange charge are as on each
broker's delivery card for the same window.

## Equity futures and options (W-178)

Fetched the same way on 2026-10-28, with zerodha.com/charges added. READ unless marked.

| # | Card rule | On the card | Page | Status | Words on the page |
|---|---|---|---|---|---|
| 8 | Zerodha futures `BROKERAGE` | MIN(0.03 %, ₹20) | zerodha.com/charges | READ | *"F&O - Futures … 0.03% or Rs. 20/executed order whichever is lower"* |
| 9 | Zerodha options `BROKERAGE` | ₹20 flat | same | READ | *"F&O - Options … Flat Rs. 20 per executed order"* |
| 10 | Upstox futures / options `BROKERAGE` | MIN(0.05 %, ₹20) / ₹20 flat | upstox.com/brokerage-charges/ | READ | *"Equity Futures \| ₹20 per executed order or 0.05% (whichever is lower) \| Equity Options \| Flat ₹20 per executed order."* |
| 11 | Fyers futures / options `BROKERAGE` | MIN(0.03 %, ₹20) / ₹20 flat | fyers.in/charges-list | READ | *"Options \| Flat ₹20 per executed order. … Futures \| ₹20 or 0.03% per executed order (whichever is lower)."* |
| 12 | `EXCHANGE_TXN`, 2025-04 cards | futures 0.00173 %; options 0.03503 % of premium | upstox.com/brokerage-charges/ | READ | *"Transaction charges (From 1st October 2024 to 28 February 2026) … NSE: Exchange turnover charge: 0.00173% … NSE: 0.03503% (on premium)"* |
| 13 | `EXCHANGE_TXN`, 2026-03 cards | futures 0.0018299 %; options 0.0355299 % of premium | fyers.in/charges-list | READ | *"Exchange Transaction Charges \| NSE: 0.0030699% \| NSE: 0.0030699% \| NSE: 0.0018299% \| NSE: 0.0355299% (On premium)"*. Zerodha: *"NSE: 0.00183% … NSE: 0.03553% (on premium)"*. Upstox: *"Transaction charges (From 1st March 2026) … NSE: 0.00183% … NSE: 0.03553% (on premium)"* |
| 14 | **IPFT folded into the transaction charge from 1-3-2026** | the 2026-03 F&O cards have no IPFT line | upstox.com/brokerage-charges/ | READ | *"IPFT charges \| From now on, IPFT charges will be combined with Transaction Charges."* Each 1-3-2026 rate is the old rate plus ₹9.99 per crore (cash, futures) or ₹49.99 per crore (options): 0.0030699 − 0.00297 = 0.0018299 − 0.00173 = 0.0000999; 0.0355299 − 0.03503 = 0.0004999. Fyers' page still lists *"NSE IPFT \| ₹0.01/Crore"* beside the combined rates; that is the pre-2023 figure, as in the cash cards' notes |
| 15 | `IPFT`, 2025-04 F&O cards | futures 0.0001 % (₹10 per crore); options 0.0005 % of premium (₹50 per crore) | — | **INFERRED** (owner ruling W-178 C2a) | No page or circular reached states the F&O IPFT before 1-3-2026. The figures are the increments in row 14 |
| 16 | Fyers clearing | **not modelled** (W-186) | fyers.in/charges-list | READ | *"Clearing Charges \| 0 \| 0 \| 0.0005% \| 0.009%"* (delivery, intraday, futures, options). The catalogue has no code for it; adding one also needs an `RS-CG-CHARGE` row (W-178 amendment) |

**Found, for W-185:** the shipped 2026-03 cash cards charge the combined 0.0030699 % **and** a separate IPFT
0.0001 % line, which counts IPFT twice from 1-3-2026 (row 14).

## Limits

- **No date for the brokerage rates:** neither page dates its intraday, mutual-fund or AMC figures. The cards start
  on 2025-04-01 like every first-generation card. A change inside the window would show only on an archived copy,
  which was not looked for.
- **BSE rates** and **BSDA tiers** are not read here (W-179 S1, S2).
