# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-154 — perquisite paths by rule data (D-313; audit P4; N1)
Stage           : READY — not started
Plan            : written when taken (plans/W-154.md; likely a split, as W-153)
Approved        : —
Steps done      : —
Next step       : on "start W-154": read its WI row, the audit file § W-088l1 P4 and N1 (PerquisiteNode, MotorCarPerquisite, AssetPerquisite, household / meal / expense benefit, accommodation, specified security paths), W-153's pattern (plans/W-153*.md: item facts, reach rows, isAboutItem), write plans/W-154.md (a split if > §7), STOP
Last verify     : targeted 573/0; unit -Punit 3474/0; integration 281/0 (2026-10-22, W-153d)
Waiting on      : the owner — "start W-154"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-153d: state which items are dividend income for the surcharge's 15% limit as rule data — the RATE-SUR-*-LIMIT rows' othersources.kind condition, so SurchargeNode and DividendInterestAllowance hold no kind test (D-324; W-153 closed)
Updated         : 2026-10-22, session 29 — PAUSED at the session limit, everything committed (resume at W-154)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
