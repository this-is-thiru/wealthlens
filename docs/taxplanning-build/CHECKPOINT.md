# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-080 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-080a → W-078j7b. UNVERIFIED: 10, all owned by W-078j7b. Open OQs: OQ-25, OQ-26. Unplaced proposals: D-210 C2(b) (PackAssembler takes the instruments); W-080's SalaryRun rename (report)
Last verify     : unit -Punit → 2820 run, 0 fail, 0 Checkstyle (W-080, 2026-10-17)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-080: run DR 08 T6 as golden tests — T6-1 ₹5,400, T6-3 nil in TY 2025-26, T6-6 ₹2,64,000 / ₹3,04,000 and T6-7 ₹50,000; the head-loss cases placed on W-081 (D-211)
Updated         : 2026-10-17, session 20 (W-080 committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
