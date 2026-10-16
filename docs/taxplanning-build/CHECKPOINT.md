# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j6a DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078j6b (DED-DONATION-HALF, -QUALIFYING, DED-EDUCATION-LOAN, DED-SPECIFIED-DISEASE, DED-WITHIN-GROSS-TOTAL, RATE-ROUND-PAYABLE, -TOTAL-INCOME (effectiveFrom fix, D-188 C2a), the 1961 Gazette for SAL-SALARY-FOR-RPF + the lock's act) → W-078j7 → W-078k … Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2600 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j6a, 2026-10-16)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none
Last item commit: taxplanning W-078j6a: read the 2012–2018 Finance Acts behind 14 introducedBy precedents and give each its Gazette reference and date (D-188)
Updated         : 2026-10-16, session 18
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
