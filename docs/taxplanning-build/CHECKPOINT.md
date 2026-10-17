# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-079d DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-079e → W-079f → W-079g → W-074b → W-079a → W-080 → W-080a → W-078j7b. UNVERIFIED: 9, all owned by W-078j7b. Open OQ: OQ-25. Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2675 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-079d, 2026-10-17)
Waiting on      : the owner — next pick. Open OQs: OQ-25
Files touched   : none
Last item commit: taxplanning W-079d: add the taxable interest on a person's own provident-fund contributions to other sources — the fund's taxable-account interest, with the excess over ₹5,00,000 / ₹2,50,000 measured per fund, in both years (D-198, D-199)
Updated         : 2026-10-17, session 20 (W-079d committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
