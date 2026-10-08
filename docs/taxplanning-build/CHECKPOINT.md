# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078e2 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Backlog order: W-078f → W-078g → W-078h → W-078i → W-079 → W-075c5 → W-074b → W-079a → W-080 → W-080a → … → W-089 → W-089a–d → W-090 … CoverageBacklogTest fails the build on a PLANNED pointer at a missing/done item and on any BUILT_FIRST section. Scratchpad (relock helper, PDFs) is lost on shutdown — recreate per memory rulepack-lock-recompute
Last verify     : ./mvnw clean test -pl backend -Punit → 2565 run, 0 fail, BUILD SUCCESS (W-078e2, 2026-10-08); probe caught; pointer audit clean
Waiting on      : the owner — next pick. Open: OQ-1, 2, 5–8, 10–13, 15, 18–24. Carried: TY 2025-26 goldens PROVISIONAL
Files touched   : —
Last item commit: taxplanning W-078e2: itemise the 42 first-scope gaps as W-089a–W-089d and W-112, and fail the build on any section left without an item (D-162)
Updated         : 2026-10-08, session 14 (W-078e2 committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
