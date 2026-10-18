# Capital gains — which gains fall on which rate basis (W-087b, 2026-10-18)

What decides a bucket's `RateBasisHint` and `Term`: the special-rate provisions' **conditions**. The rates are
W-088's. Read in session 21 with pypdf 6.14.0 from the official copies below.

| # | Source | Status | What it says (verbatim where quoted) |
|---|---|---|---|
| 1 | Income-tax Act, 2025, egazette 2025/265620, s.196(1), (3), (5) (pp.220–221) | READ | *"arising from the transfer of a short-term capital asset–– (a) being an equity share in a company or a unit of an equity oriented fund or a unit of a business trust; and (b) the transaction of sale of such equity share or unit is chargeable to securities transaction tax under Chapter VII of the Finance (No. 2) Act, 2004"* → 20%. (3): (1)(b) does not apply on an IFSC exchange for foreign-currency consideration. (5): "equity oriented fund" as in s.198 |
| 2 | Same, s.197(1), (6) (pp.221–222) | READ | *"includes any income arising from the transfer of a long-term capital asset which is chargeable under the head 'Capital gains'"* → 12.5% on such gains; no asset condition |
| 3 | Same, s.198(1), (4), (5), (8) (pp.222–223) | READ | *"Irrespective of anything contained in section 197 … (b) … a long-term capital asset being an equity share in a company or a unit of an equity oriented fund or a unit of a business trust; (c) securities transaction tax … has— (i) in a case where the long-term capital asset is in the nature of an equity share in a company, been paid on acquisition and transfer of such capital asset; or (ii) … unit … been paid on transfer"* → 12.5% on such gains exceeding ₹1,25,000. (5): *"The Central Government may, by notification, specify the nature of acquisition in respect of which the provisions of sub-section (1)(c)(i) shall not apply."* (8): "equity oriented fund" (65% / 90%-of-90% tests) |
| 4 | Income-tax Act, 1961, thc.nic.in copy (`Income Tax Act 1961_.pdf`), ss.111A, 112, 112A (pp.461–464) | READ — a consolidated copy predating the Finance (No. 2) Act, 2019 (s.111A's Explanation still points at s.10(38)) | s.111A(1): equity share / EOF unit / business-trust unit, *"(b) such transaction is chargeable to securities transaction tax"*. s.112A(1)(iii)(a)/(b): STT paid on acquisition and transfer (share), on transfer (unit); (4) the same notification power. s.112: any other long-term capital gain |
| 5 | Finance (No. 2) Act, 2019 (egazette 2019/209695) s.32 | READ | s.111A Explanation (a): "equity oriented fund" now *"clause (a) of the Explanation to section 112A"* — the definition moved, no condition changed |
| 6 | Finance Act, 2021 (2021/226208) s.32 | READ | s.112A Explanation (a): ULIP schemes whose s.10(10D) exemption is lost added to "equity oriented fund" |
| 7 | Finance Act, 2023 (2023/244830) ss.49, 50 | READ | s.111A(3) and s.112(3) (the rebate under the long-repealed s.88) omitted |
| 8 | Finance (No. 2) Act, 2024 (2024/256436) ss.21, 29, 30, 31 | READ | s.111A, s.112, s.112A: **rates only**, split at 23-7-2024 (15% → 20%; 20% / 10% → 12.5%; ₹1 lakh → ₹1.25 lakh). s.21: s.50AA rewritten from 23-7-2024 — a Specified Mutual Fund unit acquired on or after 1-4-2023, a market-linked debenture, or an unlisted bond or debenture transferred or redeemed on or after 23-7-2024: the gain *"shall be deemed to be the capital gains arising from the transfer of a short-term capital asset"* |
| 9 | Finance (No. 2) Act, 2019; Taxation Laws (Amendment) Act, 2019; Finance Act, 2020; TOLA 2020; Finance Act, 2022; Finance Act, 2024 (interim); Finance Act, 2025; Taxation Laws (Amendment) Act, 2025; Finance Act, 2026 Part A | READ (searched) | Searched for the operative words "In section 111A / 112 / 112A of the Income-tax Act" (and "sections 111A, 112"): no other amendment — the other hits are First Schedule and surcharge references. **The marginal-note search did not work here:** it found nothing even in FA 2023, whose ss.49–50 amend both, so the notes split across lines in the text layer; this record rests on the operative-word search alone |
| 10 | The notification under 1961 s.112A(4) / S2 s.198(5) (which acquisitions without STT still qualify) | **NOT READ** | Not fetched: the Department's site returns 403 here. Its contents decide equity bought without STT (bonus, rights, IPO allotment, off-market) |

**Conclusion, for TY 2025-26 and TY 2026-27 alike:** the conditions are the same in both Acts.
- A short-term gain on an equity share, an equity-oriented fund unit or a business-trust unit whose sale bore STT →
  `SPECIAL_RATE_LISTED_EQUITY`.
- Any other short-term gain → `SLAB` (no special provision charges it). A s.50AA asset is short-term whatever its
  holding.
- A long-term gain on such an asset, with STT paid on acquisition and transfer (a share) or on transfer (a unit) →
  `SPECIAL_RATE_LISTED_EQUITY`.
- Any other long-term gain → `SPECIAL_RATE_OTHER` (S2 s.197; 1961 s.112), **except** a share bought without STT
  whose acquisition the notification (row 10) covers. That case is unread.
