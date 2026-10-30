# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-190 — NOT STARTED (next on the board after W-189)
Stage           : — (W-189 done, D-400; committed as the last item commit)
Plan            : — (write plans/W-190.md on "start W-190")
Approved        : —
Steps done      : —
Next step       : on "start W-190": check STATE line 60 board order first (R4: W-189 D  W-190 ·); READ the W-190 row and audit G11; write plans/W-190.md; STOP for approval. Every item commit: board + WL-A-2 if the runbook changed; FinishedPlanTest fails a DONE item with an open plan step
Last verify     : W-200 — targeted 35/0; probes 2 bite; unit 3817/0; integration 312/0 (2026-10-30)
Waiting on      : the owner — "start W-190"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-189: put W-071 and W-072 back on the board and in the backlog as D-110's split and folded parents — the rows W-071a's commit deleted, struck through like every later split (D-400)
Updated         : 2026-10-30, session 40 — W-189 done (D-400), next W-190
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
