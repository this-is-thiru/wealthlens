# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-200 — build-time check: a DONE item's plan has no open step (W-188 C2a, D-398) — NOT STARTED
Stage           : — (W-188 done, D-398; committed as the last item commit)
Plan            : — (write plans/W-200.md on "start W-200")
Approved        : —
Steps done      : —
Next step       : on "start W-200": check STATE line 60 board order first (R4: W-188 D  W-200 ·  W-189 ·); READ the W-200 row; write plans/W-200.md; STOP for approval. Every item commit: board + WL-A-2 if the runbook changed
Last verify     : W-188 — unit 3811/0 (2026-10-30; docs only)
Waiting on      : the owner — "start W-200"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-188: tick the 15 open steps of eight finished plans with the commits that did them — all done, none left; W-200 placed for a check (D-398)
Updated         : 2026-10-30, session 40 — W-188 done (D-398), next W-200
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
