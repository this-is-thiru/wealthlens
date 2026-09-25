# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-048 — the ReliefShape sealed family
Stage           : NOT_STARTED
Plan            : — (plans/W-048.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-048 section of 03-WORK-ITEMS.md and only the sections it names; write plans/W-048.md; STOP for approval. Note: the row says `taxrules/relief/…` — the package is `taxengine/rules/…` (as for coverage/, catalogue/); ReliefResult and ReliefContext were added by D-050
Last verify     : ./mvnw clean test -pl backend -Punit → 1503 pass, 0 fail, 0 Checkstyle violations (W-046b)
Waiting on      : nothing for W-048; owner to itemise the backlog gaps (STATE); D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-046b: add the First Schedule age ladders and select one by the pack's conditions — lock to 2026-27.law.6 (D-072)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
