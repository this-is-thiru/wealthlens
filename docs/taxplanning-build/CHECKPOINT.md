# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-079b DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-075c5 (Chapter VIII deposit interest, s.153 / 80TTA-80TTB; needs OtherSourceKind.OTHER_INTEREST split savings vs deposits?) → W-079c → W-079d → W-079e → W-079f → W-079g → W-074b → W-079a → W-080 → W-080a → W-078j7b. UNVERIFIED: 9, all owned by W-078j7b. Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2626 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-079b, 2026-10-16)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none
Last item commit: taxplanning W-079b: add income from other sources — interest and dividends with each year's deductions, into gross total income, and split W-079 (D-193, D-194)
Updated         : 2026-10-16, session 18 (paused at the owner's session-limit notice; Mac shut down — scratchpad wiped)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
