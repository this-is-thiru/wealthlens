# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-195 — not started (the owner's decision: keep the old stored term labels as history)
Stage           : nothing in flight; W-194 committed (a decision, no code)
Plan            : none yet
Approved        : —
Steps done      : —
Next step       : on "start W-195": run the resume check; the board order puts W-195 first; read the row
                  (03-WORK-ITEMS.md) and the docs it names (plans/W-160c.md review talk, runbook §2.5, D-365);
                  write plans/W-195.md (a decision item — explain, no build until decided); board `W-195 ·` → `E`;
                  update this file; STOP — no coding
Last verify     : W-194: unit 3819/0 (docs only; no integration run — no code changed)
Waiting on      : the owner — W-195's decision; OQ-32, OQ-33, OQ-34 open; W-206 placed, the owner's to place
Files touched   : none — W-194's files are in the last item commit
Last item commit: taxplanning W-194: record the engine's per-bucket term with the computation, to be built with W-109 — a stored computation must keep the term it decided after the law or the facts move (D-410)
Updated         : 2026-10-31, session 42 — W-194 done (D-410); STOPPED
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
