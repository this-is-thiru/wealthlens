# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j4b DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078j5 (group D: the 14 introducedBy markers — HP-* ×7 (Act 14 of 2001 etc.), PERQ-RPF-CONTRIBUTION, PERQ-RPF-INTEREST (S.O. 1046(E) Gazette copy), RET-RETRENCHMENT (Notif. 10969 Gazette copy), SAL-LEAVE-TRAVEL-* ×2 (S.O. 201(E)), SAL-SALARY-FOR-GRATUITY (DTLA 1987 assent + Gazette), SAL-SALARY-FOR-RPF) → W-078k (PDF catch) → W-079 … Remaining provenance UNVERIFIED: only the two TY 2026-27 r.280 versions (kept: defect in the text). No golden run is PROVISIONAL. Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2600 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j4b, 2026-10-11)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none (the session-17 pause note is committed separately, as a non-item docs commit)
Last item commit: taxplanning W-078j4b: mark the RPF interest rate and the retrenchment limit SOURCE_CHECKED on the owner's copies and walk the Income-tax Rules 2026 amendments for r.280 (D-183)
Updated         : 2026-10-11, session 17 (paused at the owner's session-limit notice; Mac shut down — scratchpad wiped)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
