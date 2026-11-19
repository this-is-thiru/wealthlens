# Sourcing for W-162a — which charges reduce a capital gain (2026-10-27)

**READ** means read in the primary document by this session. Page numbers are the PDF's own. Text was extracted with
pypdf 6.14.0 from:

- the Income-tax Act, 2025, `https://egazette.gov.in/WriteReadData/2025/265620.pdf` (sha256 beginning
  `7acfb16fd7f3db8b`, the copy of `2026-10-24-speculative-transaction.md`);
- the thc.nic.in consolidation of the Income-tax Act, 1961,
  `https://thc.nic.in/Central%20Governmental%20Acts/Income%20Tax%20Act%201961_.pdf` (8,812,268 bytes, sha256
  beginning `f008674f055d3002`, current to Act 7 of 2019).

## The texts

| # | Provision | Text | Level | Page |
|---|---|---|---|---|
| C1 | 2025 s.72(1) | *"Income chargeable under the head "Capital gains" shall be computed, by deducting from the full value of the consideration received or accruing as a result of the transfer of the capital asset, the following amounts:— (a) expenditure incurred wholly and exclusively in connection with such transfer; and (b) the cost of acquisition of the asset and the cost of any improvement thereto."* Marginal note: "Mode of computation of capital gains." | READ | 117 |
| C2 | 2025 s.72(3) | *"In computing the income chargeable under the head "Capital gains", the following amounts shall not be allowed as a deduction:— (a) the interest claimed as deduction under section 22(1)(b) or under Chapter VIII; (b) any sum paid as securities transaction tax under Chapter VII of the Finance (No.2) Act, 2004."* | READ | 117 |
| C3 | 2025 s.72(2), (4) – (8) | indexation for s.197(3)'s item B; a business trust's distributions; s.67(10)'s specified entity; a non-resident's shares or debentures in foreign currency; a rupee-denominated bond's appreciation; the definitions (Cost Inflation Index, indexed cost) | READ — not modelled | 117–118 |
| C4 | 1961 s.48, clauses (i), (ii) | *"The income chargeable under the head "Capital gains" shall be computed, by deducting from the full value of the consideration received or accruing as a result of the transfer of the capital asset the following amounts, namely:— (i) expenditure incurred wholly and exclusively in connection with such transfer; (ii) the cost of acquisition of the asset and the cost of any improvement thereto"* (subs. by Act 18 of 1992, s.24, w.e.f. 1-4-1993) | READ | 272 |
| C5 | 1961 s.48, sixth proviso | *"Provided also that no deduction shall be allowed in computing the income chargeable under the head "Capital gains" in respect of any sum paid on account of securities transaction tax under Chapter VII of the Finance (No. 2) Act, 2004 (23 of 2004)."* (ins. by Act 23 of 2004, s.12, w.e.f. 1-4-2005) | READ | 272 |
| C6 | 1961 s.48, first to fifth provisos, Explanation | the non-resident's currency rule, indexation and its exclusions, a rupee-denominated bond, a gift of s.47(iii) securities; the definitions | READ — not modelled | 272–273 |

## The amending Acts the TY 2025-26 versions cite

The thc copy's footnotes give the Act and the date of effect, not its date. Both Acts were read in the Legislative
Department's annual volumes *Acts of the Parliament of India* on Wikimedia Commons, the copies the owner accepted as
verified in D-215. Each volume prints the Act's number and assent date, not its Gazette issue. Both pages were checked by eye.

| # | Act | Copy | What it says | Level | Page |
|---|---|---|---|---|---|
| C7 | Finance Act, 1992 (18 of 1992) | `https://commons.wikimedia.org/wiki/File:Acts_of_the_Parliament_of_India_1992.djvu` (sha256 beginning `924309a9374dbeec`), read with `djvutxt` and a rendered page | *"No. 18 of 1992 [14th May, 1992.]"*; s.24: *"For section 48 of the Income-tax Act, the following section shall be substituted, with effect from the 1st day of April, 1993, namely:— '48. The income chargeable under the head "Capital gains" shall be computed, by deducting from the full value of the consideration received or accruing as a result of the transfer of the capital asset the following amounts, namely:— (i) expenditure incurred wholly and exclusively in connection with such transfer; (ii) the cost of acquisition of the asset and the cost of any improvement thereto"* | READ (D-215 copy) | DjVu pp.97, 114 (printed p.108) |
| C8 | Finance (No. 2) Act, 2004 (23 of 2004) | `https://commons.wikimedia.org/wiki/File:Acts_of_the_Parliament_of_India_2004.pdf` (sha256 beginning `1134d126a3aae408`), image-only; pages rendered | *"No. 23 of 2004 [10th September, 2004.]"*; s.12: *"In section 48 of the Income-tax Act, after the fourth proviso and before the Explanation, the following proviso shall be inserted with effect from the 1st day of April, 2005, namely:— 'Provided also that no deduction shall be allowed in computing the income chargeable under the head "Capital gains" in respect of any sum paid on account of securities transaction tax under Chapter VII of the Finance (No. 2) Act, 2004.'"* | READ (D-215 copy) | PDF pp.79, 87 (printed pp.73, 81) |

## Amendments to 1961 s.48 after the consolidation, and to 2025 s.72

Every amending Act of the chain in `2026-10-10-act-chain-2019-2026.md` (A1 – A12) was fetched from egazette on
2026-10-27. Each was searched by operative words ("In section 48 of the Income-tax Act"; for A10, its s.4 items
"(N) in section 48") and by marginal notes ("Amendment of section 48."). A11 and A12 were also searched for "In
section 72", "Amendment of section 72" and "section 72(n".

| Act | sha256 (first 16) | Hit | On clause (i), (ii) or the STT proviso? | Page |
|---|---|---|---|---|
| A1 Finance (No. 2) Act, 2019 | `90a4a84c17a74cc7` | none | — | — |
| A2 TLA 2019 | `1305fd21041b7cfd` | none | — | — |
| A3 Finance Act, 2020 | `2608fdf5eabdb4d1` | none | — | — |
| A10 TOLA 2020 | `6086b45d77ab53d8` | none | — | — |
| A4 Finance Act, 2021 | `7c3b703d86169881` | **s.18:** after clause (ii), *"(iii) in case of value of any money or capital asset received by a specified person from a specified entity referred to in sub-section (4) of section 45, the amount chargeable to income-tax as income of such specified entity under that sub-section which is attributable to the capital asset being transferred"*. The p.99 marginal note "Amendment of section 48." is the LIC Act, 1956 (*"In section 48 of the principal Act, in sub-section (2)"*) | no | 30 |
| A5 Finance Act, 2022 | `fd62df6728f1806a` | the p.64 marginal note is the CGST Act (*"Availment of input tax credit"*, 13 of 2017) | no | — |
| A6 Finance Act, 2023 | `47073290530bdbeb` | **s.22:** in clause (ii), w.e.f. 1-4-2024, *"Provided that the cost of acquisition of the asset or the cost of improvement thereto shall not include the deductions claimed on the amount of interest under clause (b) of section 24 or under the provisions of Chapter VIA"* | no (a proviso to (ii), on interest) | 26 |
| A7 Finance Act, 2024 (interim) | `61c6ab8909b8fffc` | none | — | — |
| A8 Finance (No. 2) Act, 2024 | `16bf85c2bd66af15` | **s.20:** in the second proviso, after *"where long-term capital gain arises from the transfer"*, *"(which takes place before the 23rd day of July, 2024)"* inserted w.e.f. 23-7-2024 | no (indexation) | 23 |
| A9 Finance Act, 2025 | `6eb45d58a1859b5a` | none | — | — |
| A11 TLA 2025 | `efaae5fa7915248a` | none (s.48; s.72) | — | — |
| A12 Finance Act, 2026 | `f01136356e61b253` | none (s.48; s.72) | — | — |

**Conclusion:** for TY 2025-26 (1961 Act) and TY 2026-27 (2025 Act), a capital gain is the consideration less the
transfer expenditure and the cost of acquisition, and securities transaction tax is not deducted. The words are the same
in both Acts. The 2025 Act moves the STT bar from a proviso to s.72(3)(b).

## Which charge is which — the owner's ruling (D-375 C2a)

Neither Act names a charge on a contract note other than STT. The charge catalogue
(`backend/src/main/resources/data/charges/charge-catalogue.json`) describes each charge. By the owner's ruling, the
classification the catalogue's `deductible_for_capital_gains` flag carried is kept:

| Codes | Side | Limb | Basis |
|---|---|---|---|
| `BROKERAGE`, `EXCHANGE_TXN`, `SEBI_FEE`, `IPFT`, `STAMP_DUTY`, `DP`, `GST`, `EXIT_LOAD`, `MF_TXN_FEE` | buy | cost of acquisition — 1961 s.48(ii); 2025 s.72(1)(b) | D-375 C2a |
| the same nine | sale | expenditure wholly and exclusively in connection with the transfer — 1961 s.48(i); 2025 s.72(1)(a) | D-375 C2a |
| `STT` | either | not deducted — 1961 s.48 sixth proviso; 2025 s.72(3)(b) | statute, C2, C5 |
| `AMC`, `ACCOUNT_OPENING` | — | account-level (category `SUBSCRIPTION`), billed on a cycle, never on a trade; no row | D-375 C2a |
| user-entered misc (`MISC`) | either | not deducted — the record does not say what the charge was for; a product choice, not a reading of the Acts | D-375 C4a |
