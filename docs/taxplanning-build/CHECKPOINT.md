# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045k2 — the 80C and HRA section-catalogue entries (D-060)
Stage           : NOT_STARTED
Plan            : — (plans/W-045k2.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045k2 row and only the sections it names; write plans/W-045k2.md; STOP for approval. Routes only as the rulebook quotes them. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw clean test -pl backend -Punit → 1476 pass, 0 fail, BUILD SUCCESS (W-045k)
Waiting on      : nothing for W-045k2; D-054 startup wiring UNOWNED. OQ-1..OQ-3 open
Files touched   : —
Last item commit: taxplanning W-045k: add the section catalogue and its checks — the default-regime entry; 80C and HRA split to W-045k2 (D-060)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
