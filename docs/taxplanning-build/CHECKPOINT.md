# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078k DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-079 (othersources.json, OtherSourcesNode; DR 01 R17; PF-EEINT-001) → W-075c5 → W-074b → W-079a → W-080 → W-080a → W-078j7b. UNVERIFIED: 6, owned by W-078j7b. Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2601 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078k, 2026-10-16)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none
Last item commit: taxplanning W-078k: catch only the PDF renderer's own exceptions and say which report failed, and drop the file's IllegalCatch exemption (D-192)
Updated         : 2026-10-16, session 18
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
