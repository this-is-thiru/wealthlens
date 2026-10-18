# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-084a DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-085 (CapitalGainsSummary, CapitalGainsBucket, RateBasisHint — gross buckets, no tax amounts), then W-086, W-087, W-088. Open OQs: OQ-30 (W-088). UNVERIFIED: 0. Unplaced proposals: none
Last verify     : unit -Punit → 2919 run, 0 fail, 0 Checkstyle (W-084a, 2026-10-18)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-084a: resolve several elections together over every combination of their choices — each choice priced at its best combination, on whole runs (D-236)
Updated         : 2026-10-18, session 21 — W-084a committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
