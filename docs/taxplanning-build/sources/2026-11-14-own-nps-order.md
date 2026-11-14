# Sourcing for W-114d — own NPS: s.124(3) and s.123, which is claimed first (2026-11-14)

**READ** means read in the copy, text extracted with pypdf, nothing executed. Each copy was fetched this session and
its sha256 equals the one the repo already records.

| Copy | URL | Bytes | sha256 (prefix) | Grade |
|---|---|---|---|---|
| Income-tax Act, 2025 | `egazette.gov.in/WriteReadData/2025/265620.pdf` | 6,428,678 | `7acfb16fd7f3db8b` | Gazette |
| Finance Act, 2026 | `egazette.gov.in/WriteReadData/2026/271439.pdf` | 1,600,615 | `f01136356e61b253` | Gazette |

## The 2025 Act (TY 2026-27)

| # | Provision | Text | Page |
|---|---|---|---|
| 1 | s.124(3) | *"An assessee referred to in sub-section (1), or any other assessee, being an individual, shall be allowed a deduction not exceeding ₹50000, in computation of his total income of the whole of the amount paid or deposited in the tax year by such individual in his account under a pension scheme notified or as may be notified by the Central Government."* | 160 |
| 2 | s.124(4) | A minor's account, by the parent or guardian: *"subject to the condition that the aggregate amount of deduction under sub-section (3) and this sub-section shall not exceed ₹50000"* | 160 |
| 3 | s.124(5) | *"No deduction under sub-section (3) and (4) shall be allowed in respect of the amount on which a deduction has been claimed and allowed under section 123."* | 160 |
| 4 | s.124(10) | *"Where any amount paid or deposited by the assessee has been allowed as a deduction under sub-section (3), no deduction with reference to such amount shall be allowed under section 123 for that tax year."* | 160 |
| 5 | Sch. XV para 1(y) | *"contribution made by an individual to a pension scheme notified by the Central Government, to the extent of –– (i) 10% of salary, including dearness allowance, if the terms of employment so provide, but excluding all other allowances and perquisites, during the tax year in the case of an employee of the Central Government or any other employer; or (ii) 20% of gross total income during the tax year in the case of any other individual"* | 563 |

## The amending Act

| # | Act | Search | Result |
|---|---|---|---|
| 6 | Finance Act, 2026 | regex `In section 12[34]\b`, `section 124\(`, `section 123\(`, `Schedule XV`, `pension scheme` over the extracted text; and `124\|Schedule XV` | No amendment to s.123, s.124 or Schedule XV. The one "124" hit is the Finance Act's own s.124, amending Schedule IV (p.68) |

## The 1961 Act (TY 2025-26)

| # | Provision | Text | Copy |
|---|---|---|---|
| 7 | s.80CCD(1B) | *"whether or not any deductions is allowed under sub-section (1), of the whole of the amount paid or deposited … which shall not exceed fifty thousand rupees"*; no deduction for an amount allowed under (1); inserted by Act 20 of 2015 s.18 | `sources/2026-10-07-chapter-via.md` B6 — **thc.nic.in base text, not a Gazette copy**; the owner ruled C2a (D-669) to apply the order to both years on it |

## What follows

The Act bars only the **same rupee** under both s.123 and s.124(3) (rows 3, 4). It sets no order between them, and the
claim is the assessee's. Claiming own NPS under s.124(3) first never deducts less: every rupee there is deducted,
while a rupee under s.123 may meet a full ceiling. A minor's deposit (row 2) has no other home, so it takes s.124's
ceiling first. W-114d builds that order (D-669).
