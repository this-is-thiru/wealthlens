# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-201 — NOT STARTED (the Export | Stocks - Long/Short request; next on the board after W-193)
Stage           : — (W-193 done, D-404; committed as the last item commit)
Plan            : — (write plans/W-201.md on "start W-201")
Approved        : —
Steps done      : —
Next step       : on "start W-201": check board order (R4: W-193 D  W-201 ·  W-194 ·); READ the W-201 row; confirm the dropped filter by a request/test before planning a fix; write plans/W-201.md; STOP. UNPUSHED: api-collection main e4f43a2 must be pushed BEFORE this branch
Last verify     : W-192 — charge classes 128/0; unit 3817/0; integration 312/0 (2026-10-30)
Waiting on      : the owner — "start W-201"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-193: drop the two long/short-term holding requests from api-collection — the endpoints W-160e removed; W-201 placed for the Long/Short export request (D-404)
Updated         : 2026-10-30, session 40 — W-193 done (D-404), next W-201
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
