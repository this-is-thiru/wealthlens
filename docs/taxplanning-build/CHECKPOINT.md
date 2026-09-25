# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045d2 — old → new mapping for the remaining rulebook IDs (docs only; D-051)
Stage           : NOT_STARTED
Plan            : — (plans/W-045d2.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045d2 row and only the sections it names; write plans/W-045d2.md; STOP for approval. Names are the owner's to choose, each quoted from its own provision (OQ-4 / D-051) — propose, never invent
Last verify     : ./mvnw clean test -pl backend -Punit → 1488 pass, 0 fail, 0 Checkstyle violations (W-045o3)
Waiting on      : nothing for W-045d2; owner to itemise the backlog gaps (STATE); D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-045o3: apply the Finance Act 2026 to the 2025-Act section list — 354A inserted, 443 and 447 omitted (D-068)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
