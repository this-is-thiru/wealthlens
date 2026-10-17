# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-080a DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078j7b. UNVERIFIED: 10, all owned by W-078j7b. Open OQs: OQ-25, OQ-26, OQ-27. Unplaced proposals: D-210 C2(b) (PackAssembler takes the instruments); W-080's SalaryRun rename
Last verify     : unit -Punit → 2836 run, 0 fail, 0 Checkstyle (W-080a, 2026-10-17)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-080a: limit the leave travel concession to two surviving children — children born before 1-10-1998 and multiple births after one child excepted, journeys split by traveller where a child is left out (D-212)
Updated         : 2026-10-17, session 20 (W-080a committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
