# Charges engine

**Branch:** `feature/charges-engine-core` → `master` · 66 commits · 207 files

A rules engine for broker and AMC charges, replacing six fixed columns with validity-windowed rate
cards. Adding a charge is a data change, not a schema change.

## Read in this order

1. `docs/charges-engine/README.md` — the whole design, the ADRs, and §10 "Known limits, stated
   deliberately"
2. `docs/charges-engine/prd.md` — requirements and the open decisions as they were resolved
3. `docs/charges-engine/production-runbook.md` — every manual production step
4. Then the code, starting at `brokercharges/engine/ChargeEngine.java`

## Shape

A `ChargeScheduleEntity` is a rate card, validity-windowed per broker and scoped by asset type,
segment, exchange and plan. It holds `ChargeRule`s, each with a `ChargeBasis` — TURNOVER, FLAT,
PER_UNIT, SLAB, SCOPED_FLAT, DERIVED, FORMULA — served by one calculator.
`ChargeScheduleResolver` picks the card by specificity, `ChargeEngine` evaluates the rules in order,
and `UserChargeService` records one row per transaction carrying both the ordered lines and an
`amount_by_code` map.

Seed data is JSON under `resources/data/charges/` and enters a database only through
`POST /charges/seed`, never at startup.

## Three flags, shipped on

`app.charges.engine-enabled` is a master kill switch. `shadow-recording` prices every V2 trade with
the result ignored. `authoritative` makes the computed total the cost basis on a V2 buy.

## What reviewers should push on

- **ADR-31** — Phase B ran against 319 real transactions and the reconciliation gate had no baseline
  to compare against, because the manual charge field was never populated (₹40.72 total, 37 of 49
  comparable rows reading exactly ₹0.01). The engine was verified by other means. That is a real
  amendment to the plan, and it is the decision most worth a second opinion.
- **ADR-32** — no repricing. The cutover applies forward only, and the backfill was deleted because
  that was the only thing it did.
- **ADR-29** — instrument identity has no single source of truth: real mutual fund holdings key on
  the full scheme name and the shipped profiles on short codes, so they could only ever have matched
  by coincidence. Deliberately deferred to the priced-portfolio epic, which means exit load is
  unreachable in practice today.
- **71% of a real history cannot be priced** — every card starts 2025-04-01 and the staging history
  starts 2023-06-22. Correct behaviour, visible in the gaps report, and not fixed here.

## Gates

881 tests across both tiers, both JaCoCo gates passing, PIT at 577/578 with one known equivalent
mutant in `ChargeFormulaEvaluator`.
