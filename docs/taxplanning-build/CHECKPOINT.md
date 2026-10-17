# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-081a DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-081b (carry-forward: ledger redesign, s.110 / s.71B, s.202(2)(b)(i), (3), result field, preConstructionInstalmentsClaimed removal; classify the 31 coverage entries pointing at it). UNVERIFIED: 0. Open OQs: OQ-25 – OQ-28. Unplaced proposals: D-210 C2(b); W-080's SalaryRun rename
Last verify     : unit -Punit → 2854 run, 0 fail, 0 Checkstyle (W-081a, 2026-10-17)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-081a: set a head's loss off against the other heads within the year — house-property loss capped at ₹2,00,000 and destroyed in the new regime, order declared; DR 08 T6-2, T6-4, T6-5, T6-3 TY 2026-27 run (D-217)
Updated         : 2026-10-17, session 20 (W-081a committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
