# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-079a DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-080 → W-080a → W-078j7b. UNVERIFIED: 10, all owned by W-078j7b. Open OQs: OQ-25, OQ-26. Unplaced proposal: D-210 C2(b) (PackAssembler takes the instruments)
Last verify     : unit -Punit → 2813 run, 0 fail, 0 Checkstyle; integration → 247 run, 0 fail (W-079a, 2026-10-17)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-079a: compile and verify the shipped rule packs at startup, from the JAR — a timeline that differs from its lock now stops the boot, each year assembled with its own lock's instruments (D-210)
Updated         : 2026-10-17, session 20 (W-079a committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
