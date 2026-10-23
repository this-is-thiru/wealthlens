# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156e — salary kinds by rule data (D-343; R7)
Stage           : EXPLAINED — plan written and committed (pause commit), awaiting the owner's approval
Plan            : plans/W-156e.md (C1a employer.fund on PERQ-RPF-CONTRIBUTION; SAL-INCLUDES-PENSION-CONTRIBUTION reach row (s.16(k), OQ-14 quote); employer.kind on SAL-ENTMT-ALLOWANCE; journey.mode on SAL-LEAVE-TRAVEL-DISTANCE-RATE; C2a per-child a finding via CapShape.countsRead; no assertion changes)
Approved        : W-156 split (D-343); W-156a – d done (D-344 – D-349)
Steps done      : —
Next step       : ask the owner for W-156e's approval and C2's verdict (do NOT code); on "W-156e approved …": record it, Steps 1 – 5 of plans/W-156e.md; D-350; report; STOP. Also open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding (STATE Notes for resume, session 31)
Last verify     : targeted 204/0 (+ NodeContextRuleSetTest 20/0); unit -Punit 3642/0; integration 281/0 (2026-10-23, W-156d)
Waiting on      : the owner — W-156e approval (C1 – C3, C2's verdict); placements: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156d: take Chapter VIII's employer fund, Government salary and loan purpose from rows — employer.fund and loan.purpose conditions and DED-SCHEDULE-XV-GOVERNMENT-SALARY, so the nodes hold no fund, employer-kind or purpose test (D-349)
Updated         : 2026-10-23, session 31 — PAUSED at the session limit, everything committed (resume at W-156e approval)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
