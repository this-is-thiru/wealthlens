# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-205 — READY (placed by W-204; next on the board after W-204)
Stage           : W-204 committed (stage 6 done); nothing in flight
Plan            : — (write plans/W-205.md on "start W-205")
Approved        : —
Steps done      : —
Next step       : resume: Resume check; then on "start W-205": board order (R4: W-204 D  W-205 ·); READ the W-205 row; write plans/W-205.md; STOP
Last verify     : W-204 — probe rate card 200, transact 200 (after seed); unit 3819/0; integration 314/0 (2026-10-31)
Waiting on      : the owner — "start W-205"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-204: complete the two api-collection requests Jackson 3 refuses — an omitted field reaches the Lombok all-args constructor as null; the rate card is plan-scoped past the shipped card (D-408)
Updated         : 2026-10-31, session 41 — W-204 committed; next W-205
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
