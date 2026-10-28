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

## Limits

- **No date for the brokerage rates:** neither page dates its intraday, mutual-fund or AMC figures. The cards start
  on 2025-04-01 like every first-generation card. A change inside the window would show only on an archived copy,
  which was not looked for.
- **BSE rates** and **BSDA tiers** are not read here (W-179 S1, S2).
