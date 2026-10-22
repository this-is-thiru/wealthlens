# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-153c2 — share deductions as reach rows (s.93) (D-321)
Stage           : READY — not started
Plan            : written when taken (plans/W-153c2.md)
Approved        : —
Steps done      : —
Next step       : on "start W-153c2": read its WI row, plans/W-153c.md, W-153c1.md, OtherSourcesNode's switch arms (dividend, compensationInterest, familyPension, wholeDeducted, KindRow/WHOLE_DEDUCTED), the rows OS-COMPENSATION-INTEREST, -COMMUTED-PENSION, -DEATH-GRATUITY, -FAMILY-PENSION(+NTR), -DEEMED-DIVIDEND, OS-DIVIDEND-INTEREST's refusal role, LifePolicySums/GiftIncome untouched; list trace readers (D-321 exception); write plans/W-153c2.md, STOP
Last verify     : targeted 461/0; unit -Punit 3336/0; integration 281/0 (2026-10-22, W-153c1)
Waiting on      : the owner — "start W-153c2"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-153c1: choose the row that charges each other-sources item by its kind in rule data — RS-OS-INCOME, unit income charged by s.92(1) / s.56(1) not as a dividend, each base read from its row, an uncharged kind refused generically (W-153c split D-321; D-322)
Updated         : 2026-10-22, session 29 — W-153c1 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
