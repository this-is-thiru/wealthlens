# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-204 — READY (next on the board after W-203)
Stage           : W-203 committed (stage 6 done); nothing in flight
Plan            : — (write plans/W-204.md on "start W-204")
Approved        : —
Steps done      : —
Next step       : resume: Resume check; then on "start W-204": board order (R4: W-203 D  W-204 ·); READ the W-204 row; write plans/W-204.md; STOP
Last verify     : W-203 — charge 49/0, policy 5/0; unit 3819/0; integration 314/0 (2026-10-31)
Waiting on      : the owner — "start W-204"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-203: make both seeders refuse an unknown key and drop currency from the 39 cards — Jackson 3 ignores an unknown key by default, so a misspelt field seeded a card silently incomplete (D-407)
Updated         : 2026-10-31, session 41 — W-203 committed; next W-204
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
