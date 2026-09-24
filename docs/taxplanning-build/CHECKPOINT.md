# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045g — the rule-pack compiler (D-034)
Stage           : NOT_STARTED
Plan            : — (plans/W-045g.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045g row in 03-WORK-ITEMS, the W-045f erratum on it, and only the sections it names; write plans/W-045g.md; STOP for approval. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw test -pl backend -Punit → 1438 pass, 0 fail, 0 Checkstyle violations (W-045f)
Waiting on      : nothing. OQ-1..OQ-3 open (OQ-3 gates L2 deletion)
Files touched   : —
Last item commit: taxplanning W-045f: author the law once as a timeline, one file per identity — packs compile from it next (D-034)
Updated         : 2026-09-24, session 5
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
