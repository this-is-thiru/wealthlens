# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-196 — next on the board, not started (health-insurance cash and spreading refusals as nil lines)
Stage           : W-195 closed (D-411, docs only); nothing in flight
Plan            : plans/W-195.md (done). W-196 has no plan yet
Approved        : —
Steps done      : —
Next step       : on "start W-196": resume check, board order, read the row and the named docs
                  (plans/W-156b2.md:106; D-347), write plans/W-196.md, set the board W-196 · → E,
                  update this file, STOP with no coding.
Last verify     : W-195: unit 3819/0 (docs only); W-205: integration 319/0 (2026-10-31)
Waiting on      : the owner — whether to start W-196; OQ-32, OQ-33, OQ-34 open; W-206 (request
                  DTOs) placed · at the end of the queue, the owner's to place
Files touched   : plans/W-195.md, STATE.md, 03-WORK-ITEMS.md, post-deployment-runbook.md, this file
Last item commit: taxplanning W-195: keep §2.5's export as the history and remove the old stored term labels — the label came from the duplicate law with no rule, pack or year, so it is not an audit record (D-411)
Updated         : 2026-10-31, session 42 — W-195 done and committed; STOPPED
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
