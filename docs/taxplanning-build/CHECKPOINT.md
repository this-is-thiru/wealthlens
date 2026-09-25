# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045o2 — coverage register for the Income-tax Act, 1961 as in force for AY 2026-27 (D-066)
Stage           : NOT_STARTED
Plan            : — (plans/W-045o2.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045o2 row and only the sections it names; write plans/W-045o2.md; STOP for approval. Sources: thc.nic.in 1961 copy (to ~2019) + amending Acts since (Finance Acts already fetched in W-045n; Taxation Laws (Amendment) Acts, TOLA 2020 not yet). Reuse W-045o's chapter-table method. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw clean test -pl backend -Punit → 1486 pass, 0 fail, 0 Checkstyle violations (W-045o)
Waiting on      : nothing for W-045o2; owner to itemise the 2025-Act backlog gaps (STATE); D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-045o: register every section of the Income-tax Act 2025 — 536 parsed from the gazette, checked against the Act, DEFERRED added (D-066)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
