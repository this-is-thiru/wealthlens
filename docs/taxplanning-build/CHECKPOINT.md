# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j7 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-078k (DocumentGenerationService.convertHtmlToPdf catches Exception — catch only what Flying Saucer/iText throw) → W-079 → W-075c5 → W-074b → W-079a → W-080 → W-080a → W-078j7b. UNVERIFIED left: 6, all owned by W-078j7b. Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2600 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j6b, 2026-10-16; W-078j7 docs only)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : none
Last item commit: taxplanning W-078j7: walk the Income-tax Rules, 2026 amendments and the Department's notices to 16-10-2026 for r.279/r.280 and place the next watch as W-078j7b (D-191)
Updated         : 2026-10-16, session 18
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
