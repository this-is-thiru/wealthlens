# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078c DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Backlog order: W-078d → W-078e → W-078f → W-078g → W-078h → W-078i → W-079 → W-075c5 → W-074b → W-079a → W-080 → W-080a … (placements D-158, D-159 committed). Scratchpad (relock helper, PDFs) is lost on shutdown — recreate per memory rulepack-lock-recompute
Last verify     : ./mvnw clean test -pl backend -Punit → 2560 run, 0 fail, BUILD SUCCESS (W-078c, 2026-10-08); bite probes 2/2
Waiting on      : the owner — next pick. Open: OQ-1, 2, 5–8, 10–13, 15, 18–24. D-054 startup wiring UNOWNED. Carried: TY 2025-26 goldens PROVISIONAL; DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; D-104, D-105 proposed
Files touched   : —
Last item commit: taxplanning W-078c: leave unrealised rent out of the actual rent before the annual value — Rules 2026 r.21 and Rules 1962 r.4 read; stock-in-trade deferred (D-157)
Updated         : 2026-10-08, session 14 (W-078c committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
