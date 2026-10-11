# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j4 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078j4b (needs the owner's India Code PDF of S.O. 1046(E) / Notif. 24/2011, the 25-6-1999 s.10(10B) notification, and a ChatGPT share — prompt in plans/W-078j4.md "What I need from you") → W-078j5 (14 introducedBy) → W-078k (PDF catch) → W-079 … No golden run is PROVISIONAL now. Owner files via scratchpad/rules_in (~/Downloads blocked; Finder ⌥-drag if cp is refused). Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2600 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j4, 2026-10-11)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none (clean tree after the W-078j4 commit)
Last item commit: taxplanning W-078j4: read the General Clauses Act s.8 for the labour-Code reading and mark the salary-for-gratuity and statutory-gratuity versions SOURCE_CHECKED, so no golden run is provisional (D-181)
Updated         : 2026-10-11, session 17
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
