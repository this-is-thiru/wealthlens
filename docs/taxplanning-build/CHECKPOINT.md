# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-153 — other-sources kinds by rule data (D-313; audit P3)
Stage           : READY — not started
Plan            : written when taken (plans/W-153.md)
Approved        : —
Steps done      : —
Next step       : on "start W-153": read its WI row, the audit file § W-088l1 P3 (OtherSourcesNode:75–89, :148–159, :169, :192, :210, :313; DividendInterestAllowance:74, :78; DepositInterestDeduction:51–53, :113–114; SurchargeNode:100), plans/W-088m2.md (the ItemFactIs pattern), the RS-OS-* rows; write plans/W-153.md (a split if > §7), STOP
Last verify     : targeted 283/0; unit -Punit 3255/0; integration 281/0 (2026-10-21, W-152)
Waiting on      : the owner — "start W-153"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-152: state the gratuity's ten-month averaging period as rule data — RET-GRATUITY-AVERAGING, its TY 2025-26 instrument sourced (Act 4 of 1988 s.6), so neither Separation nor the node holds the 10 (D-317)
Updated         : 2026-10-21, session 28 — W-152 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
