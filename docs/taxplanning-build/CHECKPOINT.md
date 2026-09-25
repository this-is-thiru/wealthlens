# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045m — warning on the old compute endpoint (OQ-3)
Stage           : NOT_STARTED — waits on the owner's answer to OQ-3
Plan            : — (plans/W-045m.md once written)
Approved        : —
Steps done      : —
Next step       : ASK the owner OQ-3 (old compute endpoint returns known-wrong tax: warn, disable, or leave? recommended: warn now, remove when replaced — STATE "Owner questions"). "leave" retires W-045m; otherwise read the row and write plans/W-045m.md, STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1481 pass, 0 fail, 0 Checkstyle violations (W-045l)
Waiting on      : owner answer to OQ-3; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-045l: replace TaxpayerFacts with the assessee family — Individual and Huf; I5 counts only the person types a pack serves (D-062)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
