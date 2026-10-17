# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-081b DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-081c (the set-off chapter's coverage close-out: 31 entries, three catalogue entries). UNVERIFIED: 0. Open OQs: OQ-25 – OQ-29. Unplaced proposals: D-210 C2(b); W-080's SalaryRun rename
Last verify     : unit -Punit → 2864 run, 0 fail, 0 Checkstyle (W-081b, 2026-10-17)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-081b: carry the house-property loss forward eight years against house-property income — ledger per year of origin, the new regime's attributable part destroyed, order declared (D-218)
Updated         : 2026-10-17, session 20 (W-081b committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
