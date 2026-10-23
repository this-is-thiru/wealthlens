# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156b — health insurance by rule data (D-343; R3, R4; may split b1 / b2)
Stage           : NOT STARTED — plan to write
Plan            : (none yet) — HealthInsuranceDeduction (270 lines): kind checks :90, :94, the kind switch :100–123, :174; bucket by relationship :125–153
Approved        : W-156 split (D-343); W-156a done (D-344)
Steps done      : —
Next step       : read HealthInsuranceDeduction whole and its rows, write plans/W-156b.md, explain it, ask approval (do NOT code). Also still open: where to place the REG basis-set finding (STATE Notes for resume, session 30)
Last verify     : targeted 198/0; unit -Punit 3636/0; integration 281/0 (2026-10-23, W-156a)
Waiting on      : the owner — placement of the REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156a: take a donation's share, limit and donee-report bar from rows — donation.clause conditions on DED-DONATION-WHOLE / -HALF / -QUALIFYING and DED-DONATION-UNREPORTED, so DonationDeduction holds no clause test (W-156 split D-343; D-344)
Updated         : 2026-10-23, session 31 — W-156a approved and committed; next W-156b
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
