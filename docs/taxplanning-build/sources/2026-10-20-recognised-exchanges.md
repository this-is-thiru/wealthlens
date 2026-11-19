# Recognised stock exchanges and IFSC exchanges (W-088j2c1, 2026-10-20)

What W-088j2c2's chargeability rows may name:
- an exchange that is *"a recognised stock exchange"* (Chapter VII s.97(10) → SCRA 1956 s.2(f));
- one *"located in an International Financial Services Centre"* (s.113A(b)).

**READ** = this session read the source.

| # | Source | Status | What it says |
|---|---|---|---|
| 1 | Securities Contracts (Regulation) Act, 1956 — thc.nic.in copy (`Securities Contracts (Regulation) Act, 1956.pdf`, 48 pages, image-only, iLovePDF 2021-04-19): **a printed edition** held by the High Court judges' library at Agartala (stamped "Judges Library … Agartala, Tripura", accession 12-03-2021), not a Gazette copy; read from the page images | READ (a printed edition) | **s.2(f)** (p.6): *"'recognised stock exchange' means a stock exchange which is for the time being recognised by the Central Government under Section 4"*. **s.4(1)** (p.10): *"If the Central Government is satisfied … it may grant recognition to the stock exchange subject to the conditions imposed upon it as aforesaid and in such form as may be prescribed."* **s.29-B** (p.45, *"Ins. by Act 50 of 2019, S. 33 and Sch. II, Part IV (w.e.f. 1-10-2020)"*): the powers exercisable by SEBI under the Act *"(a) shall not extend to an International Financial Services Centre set up under sub-section (1) of Section 18 of the Special Economic Zones Act, 2005 …; (b) shall be exercisable by the International Financial Services Centres Authority …"* |
| 2 | SEBI, "Details of Stock Exchanges" (`sebi.gov.in/stock-exchanges.html`, fetched 2026-10-20; the page says *"last updated on January 17, 2020"*) | READ (an official list, dated 2020) | *"List of Stock Exchanges … Name of the Recognized Stock Exchange … Recognition Valid Upto … Segments Permitted"*: **BSE Ltd.** — PERMANENT — *"a. Equity b. Equity Derivatives …"*; **National Stock Exchange of India Ltd.** — PERMANENT — *"a. Equity b. Equity Derivatives …"*; Metropolitan Stock Exchange of India Ltd. — *"Sep 15, 2022"* — Equity …; Calcutta Stock Exchange Ltd. — PERMANENT; commodity exchanges (ICEX, MCX, NCDEX). A note lists 19 regional exchanges *"granted exit by SEBI"*. No IFSC exchange is on it |
| 3 | IFSCA, "Market Infrastructure Institutions" (`ifsca.gov.in/Pages/Contents/Market_Infrastructure_Institutions`, fetched 2026-10-20) | READ (official) | *"At present, there are two Stock Exchanges, two Clearing Corporations and one Depository in IFSC."* *"List of registered Market Infrastructure Institutions … 1 Stock Exchange India International Exchange (IFSC) Limited 2 Stock Exchange NSE IFSC Limited …"*; under the IFSCA (Market Infrastructure Institutions) Regulations, 2021 |
| 1a | Same edition, **s.5** (p.13) | READ (a printed edition) | **(1):** if the Central Government is of opinion that recognition *"should, in the interest of the trade or in the public interest, be withdrawn"*, after a written notice and a hearing it *"may withdraw, by notification in the Official Gazette, the recognition granted to the stock exchange"*. **(2)** (inserted by Act 1 of 2005, w.r.e.f. 12-10-2004): where a recognised stock exchange *"has not been corporatised or demutualised or it fails to submit the scheme"* under s.4-B, or the scheme is rejected, its recognition *"shall, notwithstanding anything to the contrary contained in this Act, stand withdrawn"*, and the withdrawal is to be notified in the Official Gazette |
| 4 | The repo's exchange vocabulary | READ (the repo) | `NSE` and `BSE` only: the charge rate cards' `exchange` scope (`resources/data/charges`, 24 × NSE, 5 × BSE), `EntityExportService:82`, `ChargeScheduleEntity:67` (*"NSE and BSE levy different transaction charges"*). Read as National Stock Exchange of India Ltd. and BSE Ltd. (row 2). **"NSE IFSC Limited" is a different entity**; the string `NSE` does not name it |

**Reading for W-088j2c2:**
- **Recognised for equity:** `NSE` and `BSE` (row 2, permanent recognition).
- **Located in an IFSC:** India International Exchange (IFSC) Limited and NSE IFSC Limited (row 3). The repo has no
  string for either, so a trade recorded on them reads as an unknown string and stays `UNKNOWN`.
- **No ruling needed on IFSC exchanges:** s.113A(b)'s own words, *"a recognised stock exchange located in an
  International Financial Services Centre"*, treat them as recognised stock exchanges. SCRA s.29-B moves the
  regulator for them to IFSCA, and does not take them outside s.2(f).
- **Recognition through TY 2025-26 and TY 2026-27: owner ruling (D-297), "Accept, recorded as is".**
  - Row 2 (permanent recognition, page dated 2020) holds for both years.
  - Under s.5 (row 1a), recognition ends by a Gazette notification after notice (s.5(1)), or stands withdrawn for an
    exchange not corporatised or demutualised under s.4-B (s.5(2), a 2004–05 scheme deadline). Both exchanges are on
    SEBI's 2020 list, so neither failed it.
  - **The question as asked said "only by a withdrawal order"**, which omits s.5(2). s.5(2) was read after the ruling
    and does not change it; the report says so.
