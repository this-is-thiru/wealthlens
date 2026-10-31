# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-197 — not started (the next item on the board, after W-196)
Stage           : idle; nothing in flight; no code
Plan            : —
Approved        : —
Steps done      : —
Next step       : start W-197 (`PaidOtherThanInCash` unused: wire it to the cash bar or remove
                  it) — the resume check, the board order, read the row and its named docs,
                  plans/W-197.md, the board `·` → `E`, update this file, STOP
Last verify     : W-196: unit 3821/0, integration 319/0 (2026-10-31)
Waiting on      : the owner — OQ-32, OQ-33, OQ-34 open; W-206 (request DTOs) and W-207 (a
                  non-premium's multi-year sum) placed · at the queue's end, the owner's to place
Files touched   : this file and STATE.md (the post-commit state lines, awaiting the owner's word)
Last item commit: taxplanning W-196: give a cash-barred health payment a nil line naming the row, and keep the spreading refusal — the law bars cash, it does not bar a multi-year sum the scheme limb allows (D-412)
Updated         : 2026-10-31, session 42 — W-196 committed as 1db76af5 on the owner's "approved"; next W-197
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
