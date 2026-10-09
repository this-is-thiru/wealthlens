# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (D-163 committed, docs only)
Stage           : between items
Plan            : —
Approved        : D-163 — "Ratify as-built"; "New sourcing item"; "After W-078i" (2026-10-09)
Steps done      : —
Next step       : RESUME HERE: run the resume check; then WAIT for the owner's pick. Backlog: W-078f (its S.O. 1046(E) part waits on the owner saving the India Code PDF) → W-078g → W-078h → W-078i → W-078j (sourcing, 103 UNVERIFIED versions — split at plan) → W-079 → W-075c5 → W-074b → W-079a → W-080 → W-080a → … → W-089 → W-089a–d → W-090 … Scratchpad PDFs gone — recreate per memory rulepack-lock-recompute; PDFs: egazette 265620, 271439, 271092, 244830; thc 1961 Act; dor.gov.in IT Rules_1.pdf
Last verify     : ./mvnw test -pl backend -Punit → 2565 run, 0 fail, BUILD SUCCESS (D-163 docs, 2026-10-09)
Waiting on      : the owner — next pick. Open OQs: none (D-163). TY 2025-26 goldens PROVISIONAL until W-078j
Files touched   : —
Last item commit: taxplanning W-078e2: itemise the 42 first-scope gaps as W-089a–W-089d and W-112, and fail the build on any section left without an item (D-162)
Updated         : 2026-10-09, session 15
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
