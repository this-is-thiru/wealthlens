# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-185 — not started (the next item on the board, after W-198)
Stage           : idle; nothing in flight; no code
Plan            : —
Approved        : —
Steps done      : —
Next step       : start W-185 (the IPFT line on the thirteen 2026-03 broker cards — the 0.01 per
                  crore per side from 1-3-2026, a separate contribution beside the transaction
                  charge): the resume check, the board order, the row and its named docs,
                  plans/W-185.md, the board `·` → `E`, update this file, STOP
Last verify     : W-198: targeted 124/0, unit 3821/0, integration 319/0, dump 0 real differences of
                  338,990; law relocked 2025-26.law.118 / 2026-27.law.121 (2026-10-31)
Waiting on      : the owner — the commit of these post-commit state lines; OQ-32, OQ-33, OQ-34 open;
                  W-206 (request DTOs) and W-207 (a non-premium's multi-year sum) placed · at the
                  queue's end
Files touched   : none — the post-commit lines are in 9dac3513, so a clean tree here is expected
Last item commit: taxplanning W-198: count a set's basis row as reached and drop the two one-row regime sets — their rows are reached through the set resting on them, not by a node, so the sets existed only for an overstated rule (D-414)
Updated         : 2026-10-31, session 42 — W-198 committed as f4e5c3e3, the state lines as 9dac3513; next W-185
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
