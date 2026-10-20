# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088j2 — establishing STT for a disposal with no breakdown (D-292)
Stage           : READY — not started
Plan            : plans/W-088j2.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088j2": read the WI row, D-292, plans/W-088j.md C4, runbook §3.3, W-088u8b (rebuild), the charge engine's pricing path and user_charges; write plans/W-088j2.md (a design item: options, recommended first; R15), STOP
Last verify     : unit clean -Punit 3154 run, 0 fail; integration 269/0 (2026-10-20, W-088j)
Waiting on      : the owner — "start W-088j2"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088j: record STT as paid, not paid or unknown on each side of a disposal — no breakdown is unknown, not "no STT", so the engine refuses a gain that turns on it instead of charging slab rates (D-292)
Updated         : 2026-10-20, session 26 — W-088j done (D-292); next W-088j2
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
