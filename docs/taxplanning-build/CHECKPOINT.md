# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j3b DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078j2a (D-172: owner downloads the Income Tax Department PDFs of Notification No. 94/2009 (S.O. 3245(E)) and No. 2/2010 (S.O. 66(E)); lifts SAL-SALARY-FOR-ACCOM) → W-078j4 (lifts SAL-SALARY-FOR-GRATUITY) → W-078j5 → W-078k → W-079 … Owner files arrive via scratchpad copy (~/Downloads is blocked; memory taxplanning-owner-workflow). Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2600 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j3b, 2026-10-11)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none (clean tree after the W-078j3b commit)
Last item commit: taxplanning W-078j3b: let one year's election name a vehicle per class of taxpayer and give TY 2025-26's opt-out Form No. 10-IEA for business income (D-179)
Updated         : 2026-10-11, session 17
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
