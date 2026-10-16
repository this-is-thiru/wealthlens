# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j5 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078j6 (re-read the 21 introducedBy precedents under D-184 + the 1961 Gazette for SAL-SALARY-FOR-RPF and the 2025-26 lock's act instrument) → W-078j7 (r.280 watch) → W-078k (PDF catch) → W-079 … UNVERIFIED left: SAL-SALARY-FOR-RPF introducedBy (W-078j6), the two TY 2026-27 r.280 versions (W-078j7). Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2600 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j5, 2026-10-16)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none
Last item commit: taxplanning W-078j5: read the instruments behind 13 introducedBy markers and mark them SOURCE_CHECKED, re-citing the LTC rules to S.O. 34(E) (D-184 – D-187)
Updated         : 2026-10-16, session 18
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
