# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j3 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078j3b (D-174: model the TY 2025-26 opt-out vehicle by business income — r.21AGA(1)(a) Form No. 10-IEA by the s.139(1) due date, (b) in the return; withdrawal in Form 10-IEA; ElectionProtocol holds one vehicle per year) → W-078j2a (owner downloads ITD PDFs of Notif. 94/2009 + 2/2010) → W-078j4 → W-078j5 → W-078k → W-079 … Golden blockers: SAL-SALARY-FOR-ACCOM (j2a), SAL-SALARY-FOR-GRATUITY (j4). Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2591 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j3, 2026-10-11)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none (clean tree after the W-078j3 commit)
Last item commit: taxplanning W-078j3: read rules 2A, 2B, 3B, 4, 21AGA and 26C and mark seven TY 2025-26 versions, the default-regime election and the Rules' instrument SOURCE_CHECKED (D-173)
Updated         : 2026-10-11, session 17
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
