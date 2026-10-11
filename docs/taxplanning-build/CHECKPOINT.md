# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j2a DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078j4 (groups C, E: S.O. 1046(E) of 2011 for PERQ-RPF-INTEREST (India Code download by the owner), the 1999 retrenchment-limit notification, the Direct Tax Laws (Amendment) Act 1987's assent + Gazette, a CBDT clarification on the gratuity reading (D-107); record why the two TY 2026-27 r.280 versions stay (OQ-7); lifts SAL-SALARY-FOR-GRATUITY, the last golden blocker) → W-078j5 → W-078k → W-079 … Owner files arrive via scratchpad/rules_in (~/Downloads blocked). Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2600 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j2a, 2026-10-11)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none (clean tree after the W-078j2a commit)
Last item commit: taxplanning W-078j2a: read rule 3's 2009 substitution and 2010 corrigendum in the Department's copies and mark the remaining 33 TY 2025-26 r.3 versions SOURCE_CHECKED (D-180)
Updated         : 2026-10-11, session 17
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
