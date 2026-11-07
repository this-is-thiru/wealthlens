# Gazette watch — what a rule would flag, measured on real copies (W-236, 2026-11-07)

Fetched from `https://egazette.gov.in/WriteReadData/2026/<n>.pdf` with User-Agent `WealthLens-GazetteWatch/1.0`;
text extracted with pypdf 6.14.0; nothing executed. A measurement for choosing the watch's rule, not law.

**Instruments checked by hand:**

| No. | What | sha256 (first 16) | Page 1 | Whole document |
|---|---|---|---|---|
| 271439 | The Finance Act, 2026 (No. 4 of 2026) | `f01136356e61b253` (= `sources/2026-11-07-boundary-figures.md`) | *"MINISTRY OF LAW AND JUSTICE (Legislative Department)"*; "Finance Act" ×2; no "Ministry of Finance", no "Department of Revenue" | CBDT ×1, income-tax ×241 |
| 271092 | Income-tax Rules, 2026, G.S.R. 198(E) (2,508 pages) | `af51e6719516e015` | the Gazette header only (*"PART II Section 3 Sub-section (i) … No. 192 … CG-DL-E-20032026-271092"*) | first match on **page 1533** (the English half), 53 s with pypdf |
| 271093 | Ministry of Health and Family Welfare notification (4 pages) | `9adc8610b1cfb754` | header; English from p.3 | no match |

**A window of 100 consecutive numbers, 271400 – 271499 (30 – 31 March 2026):** all 100 fetched, none missing.

- **Page-1 rule as approved** (page 1 names *Ministry of Finance* and *Department of Revenue* and one of income-tax /
  CBDT / Finance Act): **0** flagged.
- **Any term anywhere** (income-tax, Central Board of Direct Taxes, Finance Act): 20.
- **Whole-document rule** (*Central Board of Direct Taxes*, or *Income-tax Act* with *Department of Revenue*): **18** —
  the 20 less 271475 and 271497 (Department of Revenue notices naming a Finance Act, no income-tax).

| No. | Pages | Ministry (first 3 pages, if named) | income-tax | CBDT | Finance Act | Dept of Revenue | page-1 rule | whole-doc rule |
|---|---|---|---|---|---|---|---|---|
| 271421 | 2 | ministry of finance (department of revenue) (central board o | 2 | 1 | 0 | 1 | no | **yes** |
| 271422 | 30 | — | 2 | 0 | 0 | 1 | no | **yes** |
| 271423 | 26 | — | 8 | 2 | 0 | 1 | no | **yes** |
| 271426 | 2 | ministry of finance (department of revenue) (central board o | 3 | 1 | 0 | 1 | no | **yes** |
| 271427 | 2 | ministry of finance (department of revenue) (central board o | 8 | 1 | 0 | 1 | no | **yes** |
| 271428 | 2 | ministry of finance (department of revenue) (central board o | 8 | 1 | 0 | 1 | no | **yes** |
| 271429 | 2 | ministry of finance (department of revenue) (central board o | 8 | 1 | 0 | 1 | no | **yes** |
| 271435 | 71 | — | 5 | 2 | 0 | 1 | no | **yes** |
| 271436 | 133 | — | 6 | 2 | 2 | 1 | no | **yes** |
| 271439 | 121 | ministry of la w and justice (legislative department) new de | 241 | 1 | 20 | 0 | no | **yes** |
| 271441 | 135 | — | 12 | 2 | 2 | 1 | no | **yes** |
| 271442 | 164 | — | 15 | 2 | 4 | 1 | no | **yes** |
| 271443 | 92 | — | 17 | 2 | 0 | 1 | no | **yes** |
| 271444 | 6 | — | 7 | 2 | 0 | 1 | no | **yes** |
| 271445 | 6 | — | 8 | 2 | 0 | 1 | no | **yes** |
| 271454 | 2 | ministry of finance (department of revenue) (central board o | 2 | 1 | 0 | 1 | no | **yes** |
| 271473 | 2 | ministry of finance (department of revenue) (central board o | 4 | 2 | 0 | 1 | no | **yes** |
| 271474 | 2 | ministry of finance (department of revenue) (central board o | 3 | 2 | 0 | 1 | no | **yes** |
| 271475 | 56 | — | 0 | 0 | 1 | 1 | no | no |
| 271497 | 6 | ministry of finance (department of revenue) notification new | 0 | 0 | 2 | 1 | no | no |
