# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088f2b — S.O. 5054(E)'s waiver as rule data (D-289)
Stage           : READY — not started
Plan            : plans/W-088f2b.md not yet written (outline in plans/W-088f2.md)
Approved        : —
Steps done      : —
Next step       : on "start W-088f2b": read the WI row, D-289, plans/W-088f2.md (f2b outline, C3), sources row 26, ItemFacts / ConditionJson / AllOf; write plans/W-088f2b.md (R15: "Post-deployment actions"), STOP
Last verify     : unit clean -Punit 3114 run, 0 fail; integration 269/0 (2026-10-20, W-088f2a)
Waiting on      : the owner — "start W-088f2b"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088f2a: choose the capital-gains part from rule rows on the bucket's facts — D-241's STT mapping moved out of Java into RS-CG-PART, RateBasisHint deleted, every case unchanged (D-289, D-290)
Updated         : 2026-10-20, session 26 — W-088f2a done (D-290); next W-088f2b
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
