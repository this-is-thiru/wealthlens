# Charges Engine — production runbook (moved)

**Moved to the one deploy runbook,
[`../taxplanning-build/post-deployment-runbook.md`](../taxplanning-build/post-deployment-runbook.md)** (W-176,
D-379), which runs the charge engine, the trade ledger and tax planning in one order from a fresh start. The history
of this file is in git. Where each section went:

## 0. The one thing that will bite you
Deploy runbook §1B ("Why") and §1.5.

## 1. Before deploying
Deploy runbook §0.1.

## 2. Immediately after deploying — seed the rate cards
Deploy runbook §1B, after §1A clears the engine's data.

## 3. Verify before letting real trades through
Deploy runbook §1B step 3.

## 4. Registering demat accounts (only if AMC is wanted)
Deploy runbook §5.1.

## 5. Recurring manual operations
Deploy runbook §5.2.

## 6. History that predates the deployment is not priced
Deploy runbook §5.3.

## 7. If something goes wrong
Deploy runbook §5.4.

## 8. Known limits — recorded, not defects
Deploy runbook §5.5.

## Before deploying TL-7 (trade idempotency) — check for duplicates first
Deploy runbook §0.2 (the checks), §1.6 (`Could not create index`) and §5.6 (the idempotency window).
