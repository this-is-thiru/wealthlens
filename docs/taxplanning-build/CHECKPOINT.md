# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j6b DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078j7 (r.280 watch: Rules 2026 amendments after 8-10-2026 + CBDT; and re-search FA 1966, 1968 s.30, 1976, 1995 and the 1961 Gazette, D-190) → W-078k (PDF catch) → W-079 … Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2600 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j6b, 2026-10-16)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none
Last item commit: taxplanning W-078j6b: read the 1966–2007 Acts behind the last 7 introducedBy precedents, correct two RATE-ROUND effective dates and an FA 2007 section, and leave three UNVERIFIED (D-188 – D-190)
Updated         : 2026-10-16, session 18
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
