# Broker charge pages — Upstox and Fyers intraday, mutual funds and AMC (W-177, 2026-10-28)

The eight cards W-177 adds cite this record (`plans/W-177.md`; D-383 G1 – G3). **READ** means this session fetched
the page with `curl` (UA "Mozilla/5.0", HTTP 200), removed `<script>` and `<style>`, took the text between tags,
and quoted it below. No summary sits between the page and these words. That is the difference from the coverage
review, which read the pages through WebFetch.

**Re-sourced under W-187 (D-396), 2026-10-30.** The owner's rulings: a broker's own page is trusted for **that
broker's own price only** (brokerage, AMC, DP), never for an exchange or statutory figure (R4); **NSE's own circulars
are trusted** for NSE's charges (R3). So rows 1, 3 – 11 stand as read, now *READ (broker's own page, D-396)*, and
every NSE figure (rows 2, 12 – 15) is quoted from NSE's circular PDF, fetched from
`nsearchives.nseindia.com/content/circulars/<number>.pdf` and read with pypdf (table below the F&O rows).

## The figures

| # | Card rule | On the card | Page | Status | Words on the page |
|---|---|---|---|---|---|
| 1 | `UPSTOX_EQ_INTRADAY_2025_04` / `_2026_03` `BROKERAGE` | MIN(0.1 %, ₹20) | upstox.com/brokerage-charges/ | READ (broker's own page, D-396) | *"Brokerage \| Equity Delivery \| ₹20 \| per executed order \| Equity Intraday \| ₹20 per executed order or 0.1% (whichever is lower)"* |
| 2 | `UPSTOX_EQ_INTRADAY_2025_04` `EXCHANGE_TXN` | 0.00297 % | NSE/FA/64232 (row N2); upstox.com/brokerage-charges/ agrees | READ (NSE circular, D-396) | *"Transaction charges (From 1st October 2024 to 28 February 2026) \| NSE: 0.00297% per trade on buy & sell."*, printed alike for Equity Delivery and Equity Intraday. **The authority is NSE's (R3):** NSE/FA/64232, 27-9-2024, *"Cash Market: Rs. 2.97 each side per lakh of traded value"*, from 1-10-2024 (row N2). The 2026-03 card's 0.0030699 % is NSE/FA/73061, 27-2-2026, *"Rs. 306.99 each side"* per crore, from 1-3-2026 (row N3) |
| 3 | `UPSTOX_MF_2025_04` `BROKERAGE` | ₹0 | same | READ (broker's own page, D-396) | *"Zero Commission on Mutual Funds and IPOs"*; the header: *"₹ \| 0 \| Brokerage \| * \| On Mutual Funds and IPOs"* |
| 4 | `UPSTOX_MAINTENANCE_2025_04` `AMC` | ₹300 a cycle | same | READ (broker's own page, D-396) | *"2. AMC \| ZERO AMC is applicable for newly onboarded customers in the first year \| Non-BSDA users: \| AMC of ₹ 300 + GST \| BSDA Users: \| Know more"*; footnote: *"Annual Maintenance charges are free upto one year for users onboarded from 14-Feb-2025 onwards"*. The waiver and the BSDA tier are not modelled (C1a; W-179 S2) |
| 5 | `FYERS_EQ_INTRADAY_2025_04` / `_2026_03` `BROKERAGE` | MIN(0.03 %, ₹20) | fyers.in/charges-list | READ (broker's own page, D-396) | *"₹20 or 0.03% per executed order for Intraday (whichever is lower)."* fyers.in/pricing/ FAQ: *"Intraday: ₹20 or 0.03% per executed order, whichever is lower."* That page's table also shows *"₹15 \| Or 0.03% per executed order"*, the Prime plan (W-179 S3) |
| 6 | `FYERS_MF_2025_04` `BROKERAGE` | ₹0 | fyers.in/charges-list | READ (broker's own page, D-396) | *"Mutual Funds & IPO \| Zero brokerage for Mutual Funds & IPO."* fyers.in/pricing/: *"FYERS offers ₹0 brokerage on mutual funds and IPOs."* |
| 7 | `FYERS_MAINTENANCE_2025_04` `AMC` | ₹0 | fyers.in/charges-list | READ (broker's own page, D-396) | *"Demat Annual Maintenance Charges (AMC) \| ₹ 0"*. fyers.in/pricing/: *"FYERS charges ₹0 for account opening and ₹0 annual maintenance charges (AMC) for trading and demat accounts."* |

The STT, stamp duty, SEBI fee and GST these trades also carry come from the statutory cards
(`sources/2026-10-28-statutory-charge-rates.md`). The IPFT and the Fyers 2025-04 exchange charge are as on each
broker's delivery card for the same window.

## Equity futures and options (W-178)

Fetched the same way on 2026-10-28, with zerodha.com/charges added. READ unless marked.

| # | Card rule | On the card | Page | Status | Words on the page |
|---|---|---|---|---|---|
| 8 | Zerodha futures `BROKERAGE` | MIN(0.03 %, ₹20) | zerodha.com/charges | READ (broker's own page, D-396) | *"F&O - Futures … 0.03% or Rs. 20/executed order whichever is lower"* |
| 9 | Zerodha options `BROKERAGE` | ₹20 flat | same | READ (broker's own page, D-396) | *"F&O - Options … Flat Rs. 20 per executed order"* |
| 10 | Upstox futures / options `BROKERAGE` | MIN(0.05 %, ₹20) / ₹20 flat | upstox.com/brokerage-charges/ | READ (broker's own page, D-396) | *"Equity Futures \| ₹20 per executed order or 0.05% (whichever is lower) \| Equity Options \| Flat ₹20 per executed order."* |
| 11 | Fyers futures / options `BROKERAGE` | MIN(0.03 %, ₹20) / ₹20 flat | fyers.in/charges-list | READ (broker's own page, D-396) | *"Options \| Flat ₹20 per executed order. … Futures \| ₹20 or 0.03% per executed order (whichever is lower)."* |
| 12 | `EXCHANGE_TXN`, 2025-04 cards | futures 0.00173 %; options 0.03503 % of premium | NSE/FA/64232 (row N2); Upstox's page agrees | READ (NSE circular, D-396) | NSE/FA/64232 p.1: *"Equity Futures: Rs. 1.73 each side per lakh of traded value / Equity Options: Rs. 35.03 each side per lakh of premium value"*, from 1-10-2024. Upstox: *"Transaction charges (From 1st October 2024 to 28 February 2026) … NSE: Exchange turnover charge: 0.00173% … NSE: 0.03503% (on premium)"* |
| 13 | `EXCHANGE_TXN`, 2026-03 cards | futures 0.0018299 %; options 0.0355299 % of premium | NSE/FA/73061 (row N3); the pages agree | READ (NSE circular, D-396) | NSE/FA/73061 p.1, transaction charges per crore each side: futures *"Rs. 182.99"*, options *"Rs. 3,552.99"* (on premium). Zerodha's and Upstox's 0.00183 % / 0.03553 % are the circular's **totals** (Rs 183, Rs 3,553), transaction charge **plus** IPFT. Fyers: *"Exchange Transaction Charges \| NSE: 0.0030699% \| NSE: 0.0030699% \| NSE: 0.0018299% \| NSE: 0.0355299% (On premium)"*. Zerodha: *"NSE: 0.00183% … NSE: 0.03553% (on premium)"*. Upstox: *"Transaction charges (From 1st March 2026) … NSE: 0.00183% … NSE: 0.03553% (on premium)"* |
| 14 | **IPFT from 1-3-2026: rolled back to ₹0.01 per crore, still a separate contribution** (corrected by W-187; the 2026-10-28 reading said "folded into the transaction charge") | the 2026-03 F&O cards have **no** IPFT line; the 2026-03 cash cards carry **0.0001 %** (₹10). Both differ — **W-185** (D-396, RV1a) | NSE/FA/73061 (row N3) | READ (NSE circular, D-396) | *"the Exchange has now decided to roll back the increase made in the Trading Member's contributions towards NSE IPFT at the level levied prior to April 1, 2023 and revised the transaction charges accordingly, thereby ensuring no impact in the overall outflow"*; revised *"Contribution to NSE IPFT"* *"Rs. 0.01 each side"* for cash, futures and options (on premium). ₹0.01 ÷ 10⁷ × 100 = **0.0000001 %**. Upstox's *"IPFT charges will be combined with Transaction Charges"* is a broker page on an exchange figure (R4) and is wrong against the circular; Fyers' *"NSE IPFT \| ₹0.01/Crore"* is right |
| 15 | `IPFT`, 2025-04 cards (cash and F&O) | cash and futures 0.0001 % (₹10 per crore); options 0.0005 % of premium (₹50 per crore) | NSE/FA/56129 (row N1) | READ (NSE circular, D-396); was INFERRED (W-178 C2a) | p.2: *"recalibrate the contribution to NSE IPFT from existing Rs.0.01 per crore of the traded value to Rs.10 per crore of the traded value in Cash Equities Market Segment & Equity Futures and from existing Rs.0.01 per crore of the traded value (premium value) to Rs.50 per crore traded value (premium value) in Equity Options"*; *"applicable with effect from April 01, 2023"*. NSE/FA/73061's "existing" column repeats Rs 10 / 10 / 50. **Agrees** with every 2025-04 card |
| 16 | Fyers clearing | **not modelled** (W-186) | fyers.in/charges-list | READ | *"Clearing Charges \| 0 \| 0 \| 0.0005% \| 0.009%"* (delivery, intraday, futures, options). The catalogue has no code for it; adding one also needs an `RS-CG-CHARGE` row (W-178 amendment) |

### NSE circulars read (W-187, 2026-10-30)

| # | Circular | Date | Effective | What it sets | PDF (pypdf; producer, created) | SHA-256 |
|---|---|---|---|---|---|---|
| N1 | NSE/FA/56129, Ref 1/2023 | 24-3-2023 | 1-4-2023 | slab-wise transaction charges (rolled back); IPFT ₹10 / ₹10 / ₹50 per crore | `FA56129.pdf`, 2 pp.; Word 365, 2023-03-24 | `ec260386adbcf3a0…` |
| N1a | NSE/FA/61137, Ref 2/2024 | 14-3-2024 | 1-4-2024 | slab-wise transaction charges reduced; no IPFT change. Before every card window | `FA61137.pdf`, 2 pp.; Distiller 15, 2024-03-14 | `e01c5b6c76d29d55…` |
| N2 | NSE/FA/64232, Ref 5/2024 | 27-9-2024 | 1-10-2024 | uniform charges (SEBI/HO/MRD/TPD-1/P/CIR/2024/92): cash ₹2.97, futures ₹1.73 per lakh; options ₹35.03 per lakh of premium | `FA64232.pdf`, 1 p.; PDFMaker 24, 2024-09-27 | `85b816208f33bdd1…` |
| N3 | NSE/FA/73061, Ref 1 | 27-2-2026 | 1-3-2026 | per crore each side: cash 306.99 + IPFT 0.01; futures 182.99 + 0.01; options 3,552.99 + 0.01 | `FA73061.pdf`, 1 p.; Word 365, 2026-02-27 | `6085601a26177ed6…` |

N3 cites the September 2024 circular as *"NSE/FA/64323"*; N2's own PDF prints *"Download Ref No: NSE/FA/64232"*. This
record uses 64232. The leads came from the owner's ChatGPT share (session 40); every figure above is from the PDFs.

**Hand conversion:** ₹ per crore ÷ 10⁷ × 100 = %; ₹ per lakh ÷ 10⁵ × 100 = %. ₹2.97 / lakh = 0.00297 %; ₹1.73 /
lakh = 0.00173 %; ₹35.03 / lakh = 0.03503 %; ₹306.99 / crore = 0.0030699 %; ₹182.99 / crore = 0.0018299 %;
₹3,552.99 / crore = 0.0355299 %; ₹10 / crore = 0.0001 %; ₹50 / crore = 0.0005 %; ₹0.01 / crore = 0.0000001 %.

**Found, for W-185 (corrected by W-187):** from 1-3-2026 the IPFT is ₹0.01 per crore, separate (row 14). The seven
2026-03 cash cards (incl. `ZERODHA_EQ_DELIVERY_2026_06`) still carry 0.0001 % (₹10) — ₹9.99 per crore too much — and
the six 2026-03 F&O cards carry none — ₹0.01 per crore too little. W-185 corrects all thirteen to 0.0000001 % (RV1a).
The 2026-10-28 reading ("IPFT counted twice", "drop it") rested on Upstox's page.

## Limits

- **No date for the brokerage rates:** neither page dates its intraday, mutual-fund or AMC figures. The cards start
  on 2025-04-01 like every first-generation card. A change inside the window would show only on an archived copy,
  which was not looked for.
- **BSE rates** and **BSDA tiers** are not read here (W-179 S1, S2).
