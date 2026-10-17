# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-079g2b DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-079g3 → W-074b → W-079a → W-080 → W-080a → W-078j7b. UNVERIFIED: 10, all owned by W-078j7b. Open OQs: OQ-25, OQ-26. Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2799 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-079g2b, 2026-10-17)
Waiting on      : the owner — next pick. Open OQs: OQ-25, OQ-26
Files touched   : none
Last item commit: taxplanning W-079g2b: add several capped life policies to other sources — the claimed policies with the old ones within ₹2,50,000 / ₹5,00,000 in every premium year, per CBDT Circulars 2/2022 and 15/2023 (D-207)
Updated         : 2026-10-17, session 20 (W-079g2b committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
