# Hand figures for the awaiting boundaries — W-140a (2026-11-07)

**READ** means read in the copy, text extracted with pypdf, nothing executed. Arithmetic is written out below; Python was
used only as a calculator on these lines (`Fraction`, so no float rounding), never to run the engine.

| Copy | URL | Bytes | sha256 | Grade |
|---|---|---|---|---|
| Finance Act, 2025 | `egazette.gov.in/WriteReadData/2025/262125.pdf` | 1,280,178 | `6eb45d58a1859b5a25ab3d4ce9a56726121662d76b2ce3ee3b87b66274d42dd6` (first recorded) | Gazette |
| Income-tax Act, 2025 | `egazette.gov.in/WriteReadData/2025/265620.pdf` | 6,428,678 | `7acfb16fd7f3db8b4f85fc532b6d6924478ef86fa2e5b7683d282f8825f8017c` (= the repo's `7acfb16fd7f3db8b`) | Gazette |
| Finance Act, 2026 | `egazette.gov.in/WriteReadData/2026/271439.pdf` | 1,600,615 | `f01136356e61b2534328153941a68f13afd06fe8587d347757c89101d9cc564f` (first recorded) | Gazette |

Pages are the PDF's pages.

## The rates — identical in TY 2025-26 and TY 2026-27

| # | What | TY 2025-26 | TY 2026-27 | Text, in substance |
|---|---|---|---|---|
| 1 | New-regime slabs | FA 2025 s.25, inserting 1961 s.115BAC(1A)(iii) Table, p.25 | 2025 Act s.202(1) Table, p.227 | Nil to 4,00,000; 5 % to 8,00,000; 10 % to 12,00,000; 15 % to 16,00,000; 20 % to 20,00,000; 25 % to 24,00,000; *"Above … 24,00,000 30"* % |
| 2 | Old-regime slabs, under 60 | FA 2026 First Schedule Part I-A Para A (I), p.80 | Part I-B Para A (I), p.84 | Nil to 2,50,000; 5 % to 5,00,000; 12,500 + 20 % to 10,00,000; *"1,12,500 plus 30 per cent. of the amount by which the total income exceeds"* 10,00,000 |
| 3 | Old-regime slabs, 60 – 79 | Part I-A Para A (II), p.80 | Part I-B Para A (II), pp.84–85 | Nil to 3,00,000; *"5 per cent. of the amount by which the total income exceeds"* 3,00,000, to 5,00,000 |
| 3a | Old-regime slabs, 80 and over (W-141) | Part I-A Para A (III), p.80 | Part I-B Para A (III) | Nil to 5,00,000; 20 % to 10,00,000; *"Rs. 1,00,000 plus 30 per cent. of the amount by which the total income exceeds"* 10,00,000 |
| 4 | New-regime surcharge (individual, Sl. 10) | FA 2026 s.2(4)(b) Table Sl. 10, p.7 | s.3(4)(b) Table Sl. 10, pp.13–14 | 10 % above 50,00,000 to 1,00,00,000; 15 % above 1,00,00,000 to 2,00,00,000; *"(excluding dividend income or capital gains …) exceeds … 2,00,00,000, at the rate of twenty-five per cent."* |
| 5 | New-regime marginal relief | s.2(5) Table Sl. 6, pp.8–9 | s.3(5) Table Sl. 6, p.15 | Tax + surcharge *"shall not exceed"* R + (total income − C), R = tax + surcharge at C; C = 50,00,000, 1,00,00,000, 2,00,00,000 |
| 6 | Old-regime surcharge (individual, Table 1 Sl. 1) | First Schedule Part I-A Para F, pp.82–83 | Part I-B Para F, pp.86–87 | 10 % / 15 % as row 4; 25 % above 2,00,00,000 to 5,00,00,000 and *"exceeds Rs. 5,00,00,000, at the rate of 37 per cent."* (both excluding dividends and special-rate gains) |
| 7 | Old-regime marginal relief | Para F Table 2 Sl. 1, p.84 | Para F Table 2 Sl. 1 | The same formula; C = 50,00,000, 1,00,00,000, 2,00,00,000, 5,00,00,000 |
| 8 | Cess | FA 2026 s.2(6), p.9 | s.3(15), p.29 | *"four per cent."* / *"4%"* of income-tax and surcharge |
| 9 | Rebate (age case only) | 1961 s.87A — the thc.nic.in consolidation the existing T1-O cases rest on | 2025 Act s.156(1), p.186 | Resident individual, total income ≤ 5,00,000: *"100% of income-tax payable or ₹ 12500, whichever is less"* |
| 9a | New-regime rebate (W-141) | FA 2025 s.20, p.23: substitutes "twelve hundred thousand" and "sixty thousand" into the 1961 s.87A proviso and adds the slab-tax cap; the proviso's own words (FA 2023) **not read** | 2025 Act s.156(2), (3), p.186 | Not over 12,00,000: *"100% of the income-tax payable or ₹ 60000, whichever is less"*; above it, where the tax exceeds the income over 12,00,000, *"an amount equal to the amount by which the income-tax payable … is in excess of"* it; never more than the s.202(1) tax |
| 10 | Rounding of the amount payable | 1961 s.288B (thc.nic.in, as the existing cases) | 2025 Act s.516, pp.484–485 | Nearest ten rupees, paise ignored, five or more up |

Every boundary case has salary income only: no dividends, no special-rate gains, so rows 4 and 6's excluding and
including incomes are the same and clause (v)/(vi) does not arise.

## The method, checked on DR 08 first

Slab tax above the top band: new N(TI) = 3,00,000 + 30 % × (TI − 24,00,000) = 0.3·TI − 4,20,000; old O(TI) =
1,12,500 + 30 % × (TI − 10,00,000) = 0.3·TI − 1,87,500. Tax + surcharge T(TI) = slab × (1 + rate). Relief cap at the
highest C below TI: T(C) + (TI − C). Then × 1.04, then row 10's rounding.

| Case | Working | Payable | DR 08 |
|---|---|---|---|
| T2-1 new 50,00,000 | N = 10,80,000; no surcharge; × 1.04 = 11,23,200 | 11,23,200 | 11,23,200 ✓ |
| T2-3a new 51,61,190 | N = 11,28,357; × 1.10 = 12,41,192.70 > cap 10,80,000 + 1,61,190 = 12,41,190 → 12,41,190; × 1.04 = 12,90,837.60 → 12,90,840 | 12,90,840 | 12,90,840 ✓ |
| T2-3b new 51,61,200 | N = 11,28,360; × 1.10 = 12,41,196 ≤ cap 12,41,200; × 1.04 = 12,90,843.84 → 12,90,840 | 12,90,840 | 12,90,840 ✓ |
| T2-4a old 5,00,00,000 | O = 1,48,12,500; × 1.25 = 1,85,15,625; × 1.04 = 1,92,56,250 | 1,92,56,250 | 1,92,56,250 ✓ |
| T2-5 old 5,00,00,010 | cap 1,85,15,625 + 10 = 1,85,15,635; × 1.04 = 1,92,56,260.40 → 1,92,56,260 | 1,92,56,260 | 1,92,56,260 ✓ |

## The crossovers, solved

The relief binds while (1 + r) × slab(TI) > T(C) + TI − C, r the rate just above C. Solved for TI:

| Regime, C | T(C) | Inequality | Binds while TI < | Last binding / first not (₹10 grid) | Engine (D-454) |
|---|---|---|---|---|---|
| New, 1,00,00,000 | 28,38,000 | 1.15·(0.3·TI − 4,20,000) > 28,38,000 + TI − 1,00,00,000 → 0.655·TI < 66,79,000 | 1,01,96,946.56 | 1,01,96,940 / 1,01,96,950 | same ✓ |
| New, 2,00,00,000 | 64,17,000 | 1.25·(0.3·TI − 4,20,000) > 64,17,000 + TI − 2,00,00,000 → 0.625·TI < 1,30,58,000 | 2,08,92,800 | 2,08,92,790 / 2,08,92,800 | same ✓ |
| Old, 50,00,000 | 13,12,500 | 1.10·(0.3·TI − 1,87,500) > 13,12,500 + TI − 50,00,000 → 0.67·TI < 34,81,250 | 51,95,895.52 | 51,95,890 / 51,95,900 | same ✓ |
| Old, 1,00,00,000 | 30,93,750 | 1.15·(0.3·TI − 1,87,500) > 30,93,750 + TI − 1,00,00,000 → 0.655·TI < 66,90,625 | 1,02,14,694.66 | 1,02,14,690 / 1,02,14,700 | same ✓ |
| Old, 2,00,00,000 | 66,84,375 | 1.25·(0.3·TI − 1,87,500) > 66,84,375 + TI − 2,00,00,000 → 0.625·TI < 1,30,81,250 | 2,09,30,000 | 2,09,29,990 / 2,09,30,000 | same ✓ |
| Old, 5,00,00,000 | 1,85,15,625 | 1.37·(0.3·TI − 1,87,500) > 1,85,15,625 + TI − 5,00,00,000 → 0.589·TI < 3,12,27,500 | 5,30,17,826.83 | 5,30,17,820 / 5,30,17,830 | same ✓ |

At 2,08,92,800 and 2,09,30,000 the two sides are equal: the cap does not bind (it is not exceeded).

## The figures — both years

| Case | Regime | TI | Working | Payable |
|---|---|---|---|---|
| T2-6 | New | 1,00,00,000 | N = 25,80,000; × 1.10 = 28,38,000 (cap 10,80,000 + 50,00,000 not reached); × 1.04 = 29,51,520 | 29,51,520 |
| T2-7 | New | 1,00,00,010 | N = 25,80,003; × 1.15 = 29,67,003.45 > cap 28,38,000 + 10 = 28,38,010; × 1.04 = 29,51,530.40 → | 29,51,530 |
| T2-8 | New | 1,01,96,940 | N = 26,39,082; × 1.15 = 30,34,944.30 > cap 28,38,000 + 1,96,940 = 30,34,940; × 1.04 = 31,56,337.60 → | 31,56,340 |
| T2-9 | New | 1,01,96,950 | N = 26,39,085; × 1.15 = 30,34,947.75 ≤ cap 30,34,950; × 1.04 = 31,56,345.66 → | 31,56,350 |
| T2-10 | New | 2,00,00,000 | N = 55,80,000; × 1.15 = 64,17,000; × 1.04 = 66,73,680 | 66,73,680 |
| T2-11 | New | 2,00,00,010 | N = 55,80,003; × 1.25 = 69,75,003.75 > cap 64,17,000 + 10 = 64,17,010; × 1.04 = 66,73,690.40 → | 66,73,690 |
| T2-12 | New | 2,08,92,790 | N = 58,47,837; × 1.25 = 73,09,796.25 > cap 64,17,000 + 8,92,790 = 73,09,790; × 1.04 = 76,02,181.60 → | 76,02,180 |
| T2-13 | New | 2,08,92,800 | N = 58,47,840; × 1.25 = 73,09,800 = cap 73,09,800; × 1.04 = 76,02,192 → | 76,02,190 |
| T2-14 | Old | 50,00,000 | O = 13,12,500; no surcharge; × 1.04 = 13,65,000 | 13,65,000 |
| T2-15 | Old | 50,00,010 | O = 13,12,503; × 1.10 = 14,43,753.30 > cap 13,12,510; × 1.04 = 13,65,010.40 → | 13,65,010 |
| T2-16 | Old | 51,95,890 | O = 13,71,267; × 1.10 = 15,08,393.70 > cap 13,12,500 + 1,95,890 = 15,08,390; × 1.04 = 15,68,725.60 → | 15,68,730 |
| T2-17 | Old | 51,95,900 | O = 13,71,270; × 1.10 = 15,08,397 ≤ cap 15,08,400; × 1.04 = 15,68,732.88 → | 15,68,730 |
| T2-18 | Old | 1,00,00,000 | O = 28,12,500; × 1.10 = 30,93,750; × 1.04 = 32,17,500 | 32,17,500 |
| T2-19 | Old | 1,00,00,010 | O = 28,12,503; × 1.15 = 32,34,378.45 > cap 30,93,760; × 1.04 = 32,17,510.40 → | 32,17,510 |
| T2-20 | Old | 1,02,14,690 | O = 28,76,907; × 1.15 = 33,08,443.05 > cap 30,93,750 + 2,14,690 = 33,08,440; × 1.04 = 34,40,777.60 → | 34,40,780 |
| T2-21 | Old | 1,02,14,700 | O = 28,76,910; × 1.15 = 33,08,446.50 ≤ cap 33,08,450; × 1.04 = 34,40,784.36 → | 34,40,780 |
| T2-22 | Old | 2,00,00,000 | O = 58,12,500; × 1.15 = 66,84,375; × 1.04 = 69,51,750 | 69,51,750 |
| T2-23 | Old | 2,00,00,010 | O = 58,12,503; × 1.25 = 72,65,628.75 > cap 66,84,385; × 1.04 = 69,51,760.40 → | 69,51,760 |
| T2-24 | Old | 2,09,29,990 | O = 60,91,497; × 1.25 = 76,14,371.25 > cap 66,84,375 + 9,29,990 = 76,14,365; × 1.04 = 79,18,939.60 → | 79,18,940 |
| T2-25 | Old | 2,09,30,000 | O = 60,91,500; × 1.25 = 76,14,375 = cap; × 1.04 = 79,18,950 | 79,18,950 |
| T2-26 | Old | 5,30,17,820 | O = 1,57,17,846; × 1.37 = 2,15,33,449.02 > cap 1,85,15,625 + 30,17,820 = 2,15,33,445; × 1.04 = 2,23,94,782.80 → | 2,23,94,780 |
| T2-27 | Old | 5,30,17,830 | O = 1,57,17,849; × 1.37 = 2,15,33,453.13 ≤ cap 2,15,33,455; × 1.04 = 2,23,94,791.26 → | 2,23,94,790 |
| T1-A1g | Old, age 65 | 3,00,010 | Row 3: 5 % × 10 = 0.50; row 9: rebate min(0.50, 12,500) = 0.50 → tax 0; cess 0 | 0 |

"→" is row 10's rounding: paise ignored, then the last figure five or more rounds up.
