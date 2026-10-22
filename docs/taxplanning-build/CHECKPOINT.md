# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-153c — the charging row and path per kind by rule data (D-318; K1, K1r)
Stage           : READY — not started
Plan            : written when taken (plans/W-153c.md; may propose c1 / c2)
Approved        : —
Steps done      : —
Next step       : on "start W-153c": read its WI row, plans/W-153.md, W-153a/b plans, OtherSourcesNode:75–97 (IN_FULL, WHOLE_DEDUCTED, BUSINESS_HEAD_KINDS), the kind switch, dividend()/compensationInterest()/wholeDeducted() outright refusals, requireBuiltKinds, the charging OS-* rows, RS-CG-PART's BucketPart pattern; decide UNIT_INCOME's charging row from the statute (OS-DIVIDEND _note cites "any dividend" only); write plans/W-153c.md (a split if > §7), STOP
Last verify     : targeted 412/0; unit -Punit 3293/0; integration 281/0 (2026-10-22, W-153b)
Waiting on      : the owner — "start W-153c"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-153b: state which items the head's deduction rows reach as rule data — othersources.kind conditions on OS-BANKER-COMMISSION, OS-DIVIDEND-INTEREST and OS-OTHER-EXPENDITURE, so OtherSourcesNode and DividendInterestAllowance hold no kind test for them (D-320)
Updated         : 2026-10-22, session 29 — W-153b committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
