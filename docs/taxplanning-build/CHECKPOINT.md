# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j1 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-078j2 (group B r.3, ~37 versions — ask the owner for ChatGPT sourcing of the G.S.R. list amending r.3 since 2009) → W-078j3 → W-078j4 → W-078j5 → W-078k → W-079 → W-075c5 → W-074b → W-079a → W-080 → W-080a … Universal golden blockers: SAL-SALARY-FOR-ACCOM (j2), SAL-SALARY-FOR-HRA (j3), SAL-SALARY-FOR-GRATUITY (j4). Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2591 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j1, 2026-10-10)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : —
Last item commit: taxplanning W-078j1: read the 2019–2026 amending Acts for the 1961 sections of 27 TY 2025-26 versions and mark them SOURCE_CHECKED (D-170)
Updated         : 2026-10-10, session 16
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
