# Sourcing for W-088g4 — the provisions a land sale is computed without (2026-11-01)

The three provisions that can move a land or building sale's figure and that the engine does not build yet, read so
that the notice rows `CG-NOT-MODELLED-*` cite them correctly. **READ** means read in the document by this session,
text extracted with pypdf, nothing executed. The 1961 base text is the repo's thc.nic.in consolidation (current to Act
7 of 2019), as `2026-10-31-reinvestment-exemption.md` and `2026-10-31-land-building-cap.md` use it; every amendment
after it is read in the Gazette.

| Copy | URL | Bytes | sha256 (prefix) |
|---|---|---|---|
| Income-tax Act, 2025 | `egazette.gov.in/WriteReadData/2025/265620.pdf` | 6,428,678 | `7acfb16fd7f3db8b` |
| Income-tax Act, 1961, thc.nic.in | `thc.nic.in/Central%20Governmental%20Acts/Income%20Tax%20Act%201961_.pdf` | 8,812,268 | `f008674f055d3002` |
| Finance (No. 2) Act, 2019 | `egazette.gov.in/WriteReadData/2019/209695.pdf` | 1,450,193 | `90a4a84c17a74cc7` |
| Finance Act, 2020 | `egazette.gov.in/WriteReadData/2020/218938.pdf` | 769,691 | `2608fdf5eabdb4d1` |

## The provisions

| # | Provision | Text | Level | Page |
|---|---|---|---|---|
| 1 | 2025 s.78(1) | *"If the consideration received or accruing from the transfer of a capital asset, being land or building or both, is less than the stamp duty value, then, for the purposes of section 72, the stamp duty value shall be deemed to be the full value of the consideration … (b) if the stamp duty value does not exceed 110% of the consideration received or accruing from such transfer, such consideration shall be deemed to be the full value of the consideration for section 72."* | READ | 125 |
| 2 | 2025 s.72(1)(b) | *"the cost of acquisition of the asset and the cost of any improvement thereto"* | READ | 117 |
| 3 | 2025 s.90(1)(b)(i) | *"if the capital asset became the property of the previous owner or the assessee before the 1st April, 2001, means all expenditure of a capital nature incurred on or after the said date in making any additions or alterations to the capital asset"* | READ | 134 |
| 4 | 2025 s.90(9)(a), (b) | cost *"shall be the cost of acquisition of the asset to the assessee or its fair market value on the 1st April, 2001, at the option of the assessee"* (and the same for the previous owner's asset) | READ | 136 |
| 5 | 2025 s.90(10) | *"In case of a capital asset referred to in sub-section (9)(a) and (b), being land or building, or both, the fair market value of such asset on the 1st April, 2001 for the said sub-section 9(a) and (b) shall not exceed the stamp duty value, wherever available, of such asset as on the 1st April, 2001."* | READ | 137 |
| 6 | 1961 s.50C(1) | *"Where the consideration received or accruing as a result of the transfer by an assessee of a capital asset, being land or building or both, is less than the value adopted or assessed or assessable by any authority of a State Government … for the purpose of payment of stamp duty in respect of such transfer, the value so adopted or assessed or assessable shall, for the purposes of section 48, be deemed to be the full value of the consideration"* | READ (thc) | 277 |
| 7 | F(No. 2)A 2019 s.18 | s.50C(1) second proviso: *"bank account"* → *"bank account or through such other electronic mode as may be prescribed"*, from 1-4-2020 | READ | 13 |
| 8 | FA 2020 s.27 | s.50C(1) third proviso: *"five per cent."* → *"ten per cent."*, from 1-4-2021 | READ | 24 |
| 9 | 1961 s.55(1)(b)(2)(i) | *"where the capital asset became the property of the previous owner or the assessee before the 1st day of April, 2001, means all expenditure of a capital nature incurred in making any additions or alterations to the capital asset on or after the said date"* (the cost of any improvement, for s.48) | READ (thc) | 299 |
| 10 | 1961 s.55(2)(b)(i) | *"where the capital asset became the property of the assessee before the 1st day of April, 2001, means the cost of acquisition of the asset to the assessee or the fair market value of the asset on the 1st day of April, 2001, at the option of the assessee"* | READ (thc) | 302 |
| 11 | FA 2020 s.28 | inserts after s.55(2)(b)(ii), from 1-4-2021: *"Provided that in case of a capital asset referred to in sub-clauses (i) and (ii), being land or building or both, the fair market value of such asset on the 1st day of April, 2001 for the purposes of the said sub-clauses shall not exceed the stamp duty value, wherever available, of such asset as on the 1st day of April, 2001."* | READ | 24 |

## The amendment search

Each of the eleven amending Acts after the thc copy — F(No. 2)A 2019 (209695), TLA 2019 (214648), FA 2020 (218938),
TOLA 2020 (222110), FA 2021 (226208), FA 2022 (234693), FA 2023 (244830), FA 2024 (252143), F(No. 2)A 2024 (256436),
FA 2025 (262125), TLA 2025 (265618), FA 2026 (271439) — was searched on every page for `section 50C`, `section 55`
and `50C`:

- **s.50C:** rows 7 and 8 only.
- **s.55:** row 11; FA 2021 s.23 (s.55(2)(a), goodwill and like rights), FA 2023 s.31 (s.55(1)(b)(1), s.55(2)(a),
  intangibles), F(No. 2)A 2024 s.22 (s.55(2)(ac), equity grandfathering) — none touches land, s.55(1)(b)(2) or
  s.55(2)(b)(i). F(No. 2)A 2019 s.180's "section 55" is the Benami Act's, and F(No. 2)A 2024's "after section 55" is
  the same Act's s.55A.

## What this settles

The 2025 Act states the three in s.78, s.72(1)(b) with s.90(1)(b), and s.90(9)–(10); the 1961 Act in s.50C, s.48
with s.55(1)(b), and s.55(2)(b) with its FA 2020 proviso. **Not** 2025 s.55, which the W-088g4 row and the session-43
review named for the 2001 option. The notice rows cite sections only; no figure (the 110 % tolerance, the 2001 date as
a value) is copied into the engine except the 1-4-2001 boundary the row's condition needs, which rows 3, 4, 9 and 10
state.
