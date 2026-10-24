# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-160 — portfolio's holding-period law and classes (D-315; audit T1; N3, N4)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-160.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-160 (WI row; audit § W-088l3 T1; D-315, D-302 D2a), write the plan, STOP
Last verify     : targeted 437/0; unit -Punit 3714/0; integration 281/0 (2026-10-24, W-158d); W-159 docs only
Waiting on      : nothing for W-160; still open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-159: move the legacy compute path's retirement after W-123 — its check failed: taxengine serves none of the legacy endpoints yet; W-104 told of the TaxComputationService name (D-361)
Updated         : 2026-10-24, session 34 — W-159 moved after W-123 (D-361)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
