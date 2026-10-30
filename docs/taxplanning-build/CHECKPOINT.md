# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-191 — NOT STARTED (next on the board after W-190)
Stage           : — (W-190 done, D-401; committed as the last item commit)
Plan            : — (write plans/W-191.md on "start W-191")
Approved        : —
Steps done      : —
Next step       : on "start W-191": check STATE line 60 board order first (R4: W-190 D  W-191 ·); READ the W-191 row; write plans/W-191.md; STOP for approval. Every item commit: board + WL-A-2 if the runbook changed; FinishedPlanTest fails a DONE item with an open plan step
Last verify     : W-200 — targeted 35/0; probes 2 bite; unit 3817/0; integration 312/0 (2026-10-30)
Waiting on      : the owner — "start W-191"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-190: add allotment_date to the README's CORPORATE_ACTION — the one field of the entity's 16 the data model missed (D-401)
Updated         : 2026-10-30, session 40 — W-190 done (D-401), next W-191
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
