# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-075c5 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-079c → W-079d → W-079e → W-079f → W-079g → W-074b → W-079a → W-080 → W-080a → W-078j7b. UNVERIFIED: 9, all owned by W-078j7b. Open OQ: OQ-25 (s.153 gross vs net). Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2639 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-075c5, 2026-10-16)
Waiting on      : the owner — next pick. Open OQs: OQ-25
Files touched   : none
Last item commit: taxplanning W-075c5: add the deposit-interest deduction — savings interest to ₹10,000, any deposit's to ₹50,000 for a senior citizen, in both years (D-195)
Updated         : 2026-10-16, session 19
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
