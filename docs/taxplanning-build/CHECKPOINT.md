# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-046 — TaxByBasisNode + TaxByBasisNodeTest (Phase 5 opens)
Stage           : NOT_STARTED
Plan            : — (plans/W-046.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-046 section of 03-WORK-ITEMS.md and only the sections it names (DR 16 S2 step 5; DR 23 A11); write plans/W-046.md; STOP for approval. Standing rules (D-043): mark the section MODELLED in coverage.json, write/extend its catalogue entry, author both tax years (W-045n Findings table), concept rule IDs (W-045d2 mapping)
Last verify     : ./mvnw clean test -pl backend -Punit → 1489 pass, 0 fail, 0 Checkstyle violations (W-045d3)
Waiting on      : nothing for W-046; owner to itemise the backlog gaps (STATE); D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-045d3: rename the section-numbered route and test codes to concepts — lock to 2026-27.law.5, NEWREGIME now refused (D-070)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
