# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156e — salary kinds by rule data (D-343; R7; may split)
Stage           : NOT STARTED — plan to write
Plan            : (none yet) — EmployerContributionNode:140 (RPF), GrossSalaryNode:93 (NPS), SalaryDeductionNode:177 (Government allowances), AllowanceExemption:57–58 (per-child, verdict), LeaveTravelExemption:211 (per-km mode)
Approved        : W-156 split (D-343); W-156a – d done (D-344 – D-349)
Steps done      : —
Next step       : read the R7 sites whole, write plans/W-156e.md, explain it, ask approval (do NOT code). Also open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding
Last verify     : targeted 204/0 (+ NodeContextRuleSetTest 20/0); unit -Punit 3642/0; integration 281/0 (2026-10-23, W-156d)
Waiting on      : the owner — placements: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156d: take Chapter VIII's employer fund, Government salary and loan purpose from rows — employer.fund and loan.purpose conditions and DED-SCHEDULE-XV-GOVERNMENT-SALARY, so the nodes hold no fund, employer-kind or purpose test (D-349)
Updated         : 2026-10-23, session 31 — W-156d approved and committed; next W-156e
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
