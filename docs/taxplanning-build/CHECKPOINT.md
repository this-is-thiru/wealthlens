# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-192 — NOT STARTED (next on the board after W-191)
Stage           : — (W-191 done, D-402; committed as the last item commit)
Plan            : — (write plans/W-192.md on "start W-192")
Approved        : —
Steps done      : —
Next step       : on "start W-192": check STATE line 60 board order first (R4: W-191 D  W-192 ·); READ the W-192 row; write plans/W-192.md; STOP for approval. Every item commit: board + WL-A-2 if the runbook changed; FinishedPlanTest fails a DONE item with an open plan step
Last verify     : W-200 — targeted 35/0; probes 2 bite; unit 3817/0; integration 312/0 (2026-10-30)
Waiting on      : the owner — "start W-192"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-191: name the three entry points engine-enabled: false turns to 503 in CLAUDE.md and the README — the removed charge backfill dropped, authoritative shown to need shadow-recording (D-402)
Updated         : 2026-10-30, session 40 — W-191 done (D-402), next W-192
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
