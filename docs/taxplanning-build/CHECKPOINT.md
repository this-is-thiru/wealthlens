# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-079g1 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-079g2 → W-079g3 → W-074b → W-079a → W-080 → W-080a → W-078j7b. UNVERIFIED: 10, all owned by W-078j7b. Open OQs: OQ-25, OQ-26. Unrun: TaxPlanningControllerIntegrationTest (Docker)
Last verify     : ./mvnw test -pl backend -Punit → 2745 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-079g1, 2026-10-17)
Waiting on      : the owner — next pick. Open OQs: OQ-25, OQ-26
Files touched   : none
Last item commit: taxplanning W-079g1: add Keyman sums, forfeited advances, termination compensation, business-trust specified sums and the residue to other sources, and split W-079g — the business-head clauses refused, life policies to W-079g2 (D-202 – D-204)
Updated         : 2026-10-17, session 20 (W-079g1 committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
