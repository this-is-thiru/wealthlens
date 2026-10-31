# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-194 — not started (the owner's decision first: an audit record of the per-bucket term)
Stage           : nothing in flight; W-205 committed
Plan            : none yet
Approved        : —
Steps done      : —
Next step       : on "start W-194": run the resume check; the board order puts W-194 first; read the row
                  (03-WORK-ITEMS.md) and the docs it names; write plans/W-194.md (a decision item — explain, no
                  build until decided); board `W-194 ·` → `E`; update this file; STOP — no coding
Last verify     : W-205: unit 3819/0; integration 319/0; failing-first 2 errors + 2 failures then 5/0 (2026-10-31)
Waiting on      : the owner — OQ-32, OQ-33, OQ-34 open; W-206 placed for the request-DTO redesign, the owner's to place
Files touched   : none — W-205's files are in the last item commit
Last item commit: taxplanning W-205: bind the six request-bound entities through the no-arg constructor — an omitted field arrived null (a sell 500, a published card with no author); a body could also write the author (D-409)
Updated         : 2026-10-31, session 41 — W-205 done (D-409); STOPPED
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
