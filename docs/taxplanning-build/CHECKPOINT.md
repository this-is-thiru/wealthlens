# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078i DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : RESUME HERE (session 16): run the resume check; then WAIT for the owner's pick. Backlog: W-078j (sourcing: 103 UNVERIFIED versions incl. SAL-HOUSE-RENT-LANDLORD-PAN TY 2025-26, + S.O. 1046(E) — split at plan) → W-078k (convertHtmlToPdf catch(Exception), D-169) → W-079 → W-075c5 → W-074b → W-079a → W-080 → W-080a → … Unrun: TaxPlanningControllerIntegrationTest (needs Docker). Scratchpad PDFs gone after shutdown — egazette 2025/265620, 2023/244830, 2026/271092, 2016/169418, 2024/252143
Last verify     : ./mvnw test -pl backend -Punit → 2591 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078i, 2026-10-09); DocumentGenerationServiceTest 1/1; probe bit
Waiting on      : the owner — next pick. Open OQs: none. TY 2025-26 goldens PROVISIONAL until W-078j
Files touched   : —
Last item commit: taxplanning W-078i: print the known-errors notice on the tax-report PDF above every figure, as the JSON endpoint carries it (D-168)
Updated         : 2026-10-09, session 15 (paused at the owner's session-limit notice)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
