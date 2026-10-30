# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-202 — NOT STARTED (request bodies accept unknown JSON properties silently; next after W-201)
Stage           : — (W-201 done, D-405; committed as the last item commit)
Plan            : — (write plans/W-202.md on "start W-202")
Approved        : —
Steps done      : —
Next step       : on "start W-202": check board order (R4: W-201 D  W-202 ·  W-194 ·); READ the W-202 row; survey clients (api-collection, UI if in repo) for fields the server lacks before proposing strict deserialisation; write plans/W-202.md; STOP. UNPUSHED: api-collection main e4f43a2, 99d8653 — push the submodule BEFORE this branch
Last verify     : W-192 — charge classes 128/0; unit 3817/0; integration 312/0 (2026-10-30)
Waiting on      : the owner — "start W-202"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-201: re-key three api-collection requests to queryFilters — the filters key was silently ignored, shown by probe; the Long/Short export renamed to what it does; W-202 placed for the silent default (D-405)
Updated         : 2026-10-30, session 40 — W-201 done (D-405), next W-202
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
