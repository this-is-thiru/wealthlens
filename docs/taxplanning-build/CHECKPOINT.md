# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-189 — NOT STARTED (next on the board after W-200)
Stage           : — (W-200 done, D-399; committed as the last item commit)
Plan            : — (write plans/W-189.md on "start W-189")
Approved        : —
Steps done      : —
Next step       : on "start W-189": check STATE line 60 board order first (R4: W-200 D  W-189 ·); READ the W-189 row; write plans/W-189.md; STOP for approval. Every item commit: board + WL-A-2 if the runbook changed; FinishedPlanTest now fails a DONE item with an open plan step
Last verify     : W-200 — targeted 35/0; probes 2 bite; unit 3817/0; integration 312/0 (2026-10-30)
Waiting on      : the owner — "start W-189"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-200: fail the build when a finished item's plan keeps an open step — FinishedPlanTest over every plan and the whole status board, the board reader shared as testsupport.StatusBoard (D-399)
Updated         : 2026-10-30, session 40 — W-200 done (D-399), next W-189
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
