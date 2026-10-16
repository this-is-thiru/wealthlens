# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-079c DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-079d → W-079e → W-079f → W-079g → W-074b → W-079a → W-080 → W-080a → W-078j7b. UNVERIFIED: 9, all owned by W-078j7b. Open OQ: OQ-25. Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2658 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-079c, 2026-10-16)
Waiting on      : the owner — next pick. Open OQs: OQ-25
Files touched   : none
Last item commit: taxplanning W-079c: add family pension, a Government death gratuity and a fund pension's commutation to other sources — one-third within ₹15,000 / ₹25,000 new, the others in full, in both years (D-196, D-197)
Updated         : 2026-10-16, session 19
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
