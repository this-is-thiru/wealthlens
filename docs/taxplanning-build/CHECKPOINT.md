# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156c — disability by rule data (D-343; R5; N1)
Stage           : NOT STARTED — plan to write
Plan            : (none yet) — DisabilityDeduction (207 lines): severity → row (:64, :104), dependant by relationship (:164–166)
Approved        : W-156b closed (D-345 – D-347)
Steps done      : —
Next step       : read DisabilityDeduction whole and its rows, write plans/W-156c.md, explain it, ask approval (do NOT code). Also open: the nil-line proposal and PaidOtherThanInCash (W-156b2), the REG basis-set finding
Last verify     : targeted 219/0; unit -Punit 3640/0; integration 281/0 (2026-10-23, W-156b2)
Waiting on      : the owner — placement of the nil-line proposal and PaidOtherThanInCash (W-156b2) and of the REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156b2: take health insurance's payment kinds from rows — health.kind reach rows for cash, spreading, insurance paid and the limbs, so HealthInsuranceDeduction holds no kind test (D-347; W-156b closed)
Updated         : 2026-10-23, session 31 — W-156b2 approved and committed, W-156b closed; next W-156c
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
