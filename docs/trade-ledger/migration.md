# Deploying the trade-ledger branch — what has to happen to the data (moved)

**Moved to the one deploy runbook,
[`../taxplanning-build/post-deployment-runbook.md`](../taxplanning-build/post-deployment-runbook.md)** (W-176,
D-379), which runs the charge engine, the trade ledger and tax planning in one order from a fresh start. The history
of this file is in git. Where each section went:

## 1. MANDATORY — backfill `version` on `profit_and_loss`
Deploy runbook §0.3. Done: 0 documents without it on prod and staging (2026-10-27).

## 2. MANDATORY — re-seed the charge catalogue
**Retired.** It re-seeded to backfill `deductible_for_capital_gains`, which W-162c removed (D-378). The catalogue is
seeded in deploy runbook §1B, and §2.10 unsets the stored flag.

## 3. CHECK — duplicates that would block a unique index
Deploy runbook §0.2.

## 3a. If either query returns something — how to correct it
Deploy runbook §0.2, "If either query returns something".

## 4. DONE — trades dated 31 March
Deploy runbook §4 and its run log.

## 5. NO ACTION — additive fields, and the response rename
Deploy runbook §4.

## Order
Deploy runbook, "The order, from a fresh start".
