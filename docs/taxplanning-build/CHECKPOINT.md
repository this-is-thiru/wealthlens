# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-193 — NOT STARTED (next on the board after W-192)
Stage           : — (W-192 done, D-403; committed as the last item commit)
Plan            : — (write plans/W-193.md on "start W-193")
Approved        : —
Steps done      : —
Next step       : on "start W-193": check STATE line 60 board order first (R4: W-192 D  W-193 ·); READ the W-193 row; api-collection is a git submodule (memory: charge-card-gotchas); write plans/W-193.md; STOP for approval. FinishedPlanTest fails a DONE item with an open plan step
Last verify     : W-192 — charge classes 128/0; unit 3817/0; integration 312/0 (2026-10-30)
Waiting on      : the owner — "start W-193"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-192: cite the stock brokers' turnover-fee Regulations for SEBI_FEE in the catalogue, and Article 56A as inserted by FA 2019 for STAMP_DUTY — both read in the Gazette (D-403)
Updated         : 2026-10-30, session 40 — W-192 done (D-403), next W-193
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
