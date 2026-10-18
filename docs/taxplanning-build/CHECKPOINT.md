# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-079e2 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-084 (IncrementalRunner), then W-084a, W-085, W-086. Open OQs: OQ-30 (W-088). UNVERIFIED: 0. Unplaced proposals: none
Last verify     : unit -Punit → 2912 run, 0 fail, 0 Checkstyle (W-079e2, 2026-10-18)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-079e2: attribute TY 2025-26's shared dividend and unit interest allowance by income share, bounded by each side's claim — the OQ-26 Q2 refusal removed (D-234)
Updated         : 2026-10-18, session 21 — W-079e2 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
