# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156b2 — health insurance's payment kinds by rule data (D-345; H3 – H7)
Stage           : NOT STARTED — plan to write
Plan            : (none yet) — H3 cash only check-up (:90), H4 spread only premium (:94), H5 limb per kind (switch), H6 only premium raises senior, H7 s.126(7) premiums filter; see plans/W-156b.md
Approved        : W-156b split (D-345); W-156b1 done (D-346)
Steps done      : —
Next step       : write plans/W-156b2.md, explain it, ask approval (do NOT code). Also still open: where to place the REG basis-set finding (STATE Notes for resume, session 30)
Last verify     : targeted 161/0; unit -Punit 3638/0; integration 281/0 (2026-10-23, W-156b1)
Waiting on      : the owner — placement of the REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156b1: take health insurance's buckets from rows — relationship, dependant and kind conditions on the four bucket rows, so HealthInsuranceDeduction holds no relationship switch or scheme test (W-156b split D-345; D-346)
Updated         : 2026-10-23, session 31 — W-156b1 approved and committed; next W-156b2
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
