# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078e2 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : RESUME HERE (session 15): run the resume check; then WAIT for the owner's pick. Backlog order: W-078f (docs/data fixes; its S.O. 1046(E) part waits on the owner saving the India Code PDF) → W-078g (DIS-S202-2A) → W-078h (notice channel + SAL-HRA-006) → W-078i (tax-report PDF notice) → W-079 → W-075c5 → W-074b → W-079a (startup wiring) → W-080 → W-080a → … → W-089 → W-089a–d → W-090 … Guards now in the build: CoverageBacklogTest (PLANNED pointer must be live on the STATE board; no BUILT_FIRST). Session-14 scratchpad (relock.sh, Gazette/Rules/thc PDFs, generators) is gone after shutdown — recreate per memory rulepack-lock-recompute; PDFs: egazette 265620, 271439, 271092, 244830; thc 1961 Act; dor.gov.in IT Rules_1.pdf
Last verify     : ./mvnw clean test -pl backend -Punit → 2565 run, 0 fail, BUILD SUCCESS (W-078e2, 2026-10-08); probe caught; pointer audit clean
Waiting on      : the owner — next pick. Open: OQ-1, 2, 5–8, 10–13, 15, 18–24. Carried: TY 2025-26 goldens PROVISIONAL
Files touched   : —
Last item commit: taxplanning W-078e2: itemise the 42 first-scope gaps as W-089a–W-089d and W-112, and fail the build on any section left without an item (D-162)
Updated         : 2026-10-08, session 14 (paused at the owner's request — session limit; all work committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
