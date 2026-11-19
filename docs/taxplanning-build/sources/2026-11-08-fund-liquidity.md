# Sourcing for W-238e1: the withdrawal terms of NPS, the EPF and an approved superannuation fund (2026-11-08)

**Located, then read for terms at build (D-494, C1a – C4a).** The terms read are in the rows' `liquidity`
(`exec/funds/*.json`, `exec/components/EMPLOYEE_NPS.json`, `EMPLOYEE_PF.json`, `EMPLOYEE_SUPERANNUATION.json`), each
entry naming its provision. Pre-15-12-2025 NPS terms come from N0 as amended by N1 (reg. 8 substituted, item VI), N8
(the lakh thresholds; reg. 4(a)) and N9 (reg. 4(b) first para); N2 – N7 and N10 touch none of the terms stated; N12
adds only reg. 4A(2) – (4). Each copy below is an egazette file, opened and its first page and title text
extracted with pypdf; nothing executed. The regulator's own lists (pfrda.org.in regulation pages; one web search for
the EPF) were **leads only**: they gave dates and, from 2020, the `CG-DL-E` id. Every number was then fetched from
egazette.gov.in. Where the regulator's PDF and the egazette file are byte-identical, the table says so. No withdrawal
term is stated here. That happens at build, from these copies, once the plan is approved.

## A. NPS: PFRDA (Exits and Withdrawals under the National Pension System) Regulations, 2015 and all twelve amendments

The amendment list is the regulator's (`pfrda.org.in/regulatory-framework/regulations?delta=100`, 83 entries, every
"Exits and Withdrawals" row). The footnotes of the 2025 and 2026 amendments confirm the chain's ends. Each amendment
says *"These shall come into force on the date of their publication in the Official Gazette"* (2015 – 2019 not yet
read for this; at build).

| # | Instrument | Gazette issue (Part III §4) | egazette file | Bytes | sha256 | Same as regulator's PDF |
|---|---|---|---|---|---|---|
| N0 | Regulations, 2015 (No. PFRDA/12/RGL/139/8) | No. 159, Mon 11-05-2015 | `2015/164281.pdf` | 484,621 | `8541a01b7029a7043802b849f2799bf22369df7a90490382029bd29a5d654995` | no (different file, same issue) |
| N1 | (First Amendment) 2017 | No. 315, Thu 10-08-2017 | `2017/178004.pdf` | 1,451,388 | `457d638dc2a0cd098128ebd69659a5ed147172025a2cc028f43ddbcfde9ee393` | yes |
| N2 | (Second Amendment) 2017 | No. 387, Fri 06-10-2017 | `2017/179442.pdf` | 1,703,147 | `22015cf80c49a5b57549017b83b6bd581b9ed3fe82b80a773d5d96d5704ebcdf` | yes |
| N3 | (Third Amendment) 2018 | No. 40, Fri 02-02-2018 | `2018/182579.pdf` | 1,675,735 | `de1b3efbd68f1ad92e956329e51b27c8bd72ee8b54eafb1dfa0797fec4c9c19c` | yes |
| N4 | (Fourth Amendment) 2018 | No. 184, Fri 18-05-2018 | `2018/185559.pdf` | 1,836,321 | `62c6233d4cb0a2f1bf515aaebfdfb004995a537edf32d53254aebb58a5f15e46` | yes |
| N5 | (Fifth Amendment) 2019 | No. 69, Tue 19-02-2019 | `2019/198253.pdf` | 337,766 | `7dec03711540e992e4ee05eea484897d301043214d884c943b1af185bc963642` | yes |
| N6 | (Sixth Amendment) 2019 | No. 331, Mon 23-09-2019 | `2019/212713.pdf` | 323,029 | `a98032de54d227cd203f63c7081d3c83a3cfab7ada6ed469a533c1da9d52265b` | yes |
| N7 | (Amendment) 2020 — the regulator's consolidation calls it the "Seventh Amendment", w.e.f. 29.09.2020 | No. 400, Wed 30-09-2020; CG-DL-E-01102020-222174 | `2020/222174.pdf` | 1,975,770 | `51527f82bbecfc50f4082eed9c8e1275520d5cb2ac6afad4408a49df18cf6d36` | yes |
| N8 | (Amendment) 2021 | No. 239, Mon 14-06-2021; CG-DL-E-14062021-227597 | `2021/227597.pdf` | 1,278,838 | `6d619cf14b964af49fe525322b186aa4699db3e4b88edce201524b9e9a77b943` | yes |
| N9 | (Second Amendment) 2021 | No. 685, Tue 28-12-2021; CG-DL-E-29122021-232219 | `2021/232219.pdf` | 1,529,114 | `7c9d158cf3b93a8fa06cf56527e81497c7dcdd08e4a963b76f1f70bbb8f838a1` | yes |
| N10 | (Amendment) 2023 | No. 177, Thu 23-03-2023; CG-DL-E-24032023-244640 | `2023/244640.pdf` | 662,475 | `3be355613353bd1f2476da5e86bca2d51b28814e8aa10bf4794c27f739d32f23` | yes |
| N11 | (Amendment) 2025 (No. PFRDA/16/14/06/0009/2018-REG-EXIT), 22 pages | No. 808, **Mon 15-12-2025**; CG-DL-E-16122025-268548 | `2025/268548.pdf` | 2,331,320 | `e3e791e7e5cff732c880c1b55b9746a7eb8265b49e68306e90d0eb867422fab3` | yes |
| N12 | (Amendment) 2026 | No. 450, **Tue 14-07-2026**; CG-DL-E-20072026-274607 | `2026/274607.pdf` | 748,829 | `c7a8ae58fb9ce27133a49b18a23299f86129840ca11e8c01f235b80af5a5299c` | yes |

Findings on the copies:
- **F1, publication date.** The issue dates printed on N11 and N12 (15-12-2025, 14-07-2026) are earlier than the
  dates the regulator lists and the ids carry (16-12-2025, 20-07-2026). N12's own footnote says N11 was *"published in
  the Gazette of India on 16 th December 2025"*. N7 has the same gap: issue 30-09-2020, consolidation w.e.f. 29.09.2020.
  Each commences on publication, so the first day of each version is a ruling, not a reading.
- **F2, a footnote error.** N11's footnote names its predecessor as the *"(Amendment) Regulations, 2023 published in
  the Gazette of India on 23rd March, 2021"*. N10 is in the issue of 23-03-**2023**.
- **F3, mid-year changes.** N11 falls inside tax year 2025-26 and N12 inside 2026-27. Each year therefore has two
  versions of the NPS terms.
- N11 amends clause by clause (it begins at regulation 1 and continues through regulation 2 and later). It is not a
  substitution, so the text before 15/16-12-2025 is N0 as amended by N1 – N10.
- Not Gazette, used as a lead only: the regulator's consolidation "Regulations, 2015 [Last amended on 20th July 2026]"
  (37 pages, `pfrda.org.in/documents/33652/184762/…`).

## B. EPF: the Employees' Provident Funds Scheme, 2026 (and the 1952 Scheme it supersedes)

| # | Instrument | Gazette issue | egazette file | Bytes | sha256 |
|---|---|---|---|---|---|
| E1 | G.S.R. 525(E), 29-06-2026 — Employees' Provident Funds Scheme, 2026 (Code on Social Security, 2020 s.15(1)(a)), 129 pages | Part II §3(i), No. 473, Mon 29-06-2026; CG-DL-E-01072026-273957 | `2026/273957.pdf` | 2,510,370 | `4e062db5bf5d8b904ae1c0d4af10950dc01de7df8360398dfe197d6d06aef489` |
| E2 | S.O. 3582(E), 01-07-2026 — contribution rate 12% under the Code s.16(1)(a) first proviso, *"deemed to come into force from the 21 st day of November, 2025"* | Part II §3(ii); in file 274112 | `2026/274112.pdf` | 614,680 | `9dfc45128ff76a5a08e599d7d17b3ff08b8d4f9da1c3fd0180e4e217d9050a79` |

- E1 para 1(2): *"It shall come into force on the date of its publication in the Official Gazette."* Its preamble is
  *"in supersession of the Employees' Provident Funds Scheme, 1952, except as respects things done or omitted to be
  done before such supersession"*. Para 46 is *"Partial withdrawals from Fund"* (English text, print p.87 of the
  file).
- **F4, the 1952 Scheme is not located.** It governs 2025-26 and 1-4-2026 to the day before E1. Its principal text
  is a 1952 notification, older than egazette's holdings (numbered files start about 2014; pre-2012 coverage is
  sparse). Its withdrawal paragraphs (68-B onwards, 69) were amended many times. No Gazette consolidation exists.
- **F5, a recognised provident fund is not always the EPF.** An exempted establishment's trust has its own rules. E1
  governs the fund the EPFO runs.

## C. An approved superannuation fund: income-tax law bounds the fund's own rules

| # | Instrument | Where | Copy |
|---|---|---|---|
| S1 | Income-tax Act, 2025, Schedule XI Part B para 3(b) | print p.553: *"the sole purpose of the fund shall be the provision of annuities … (i) upon their retirement at or after a specified age; (ii) upon incapacitation before retirement; … or (iv) for the widows, children or dependants of such employees on their death"* | `2025/265620.pdf`, sha256 `7acfb16fd7f3db8b4f85fc532b6d6924478ef86fa2e5b7683d282f8825f8017c` (equal to the repo's record, D-455) |
| S2 | Income-tax Rules, 2026, rr.301 – 315, notably r.307 (scheme of insurance or annuity), r.308 (commutation of annuity: *"shall not exceed — (a) in a case where the employee receives any gratuity, the commuted value of one-third of the annuity …"*) | print pp.1796 – 1798 | `2026/271092.pdf`, 29,347,728 bytes, sha256 `af51e6719516e015ff53973b7d6144d782427ab547140a31de5c2b1f38c606cb` |

- **F6.** The fund's actual terms are its trust deed, which differs for each employer. The law gives only the bound:
  money comes out as an annuity on retirement, incapacity or death, with commutation capped. For 2025-26 the 1961
  Act (Fourth Schedule Part B) and the 1962 Rules govern; neither was located in this session.
