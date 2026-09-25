# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045o3 — apply the Finance Act, 2026 to the Income-tax Act, 2025 section list (D-067)
Stage           : NOT_STARTED
Plan            : — (plans/W-045o3.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045o3 row and only the sections it names; write plans/W-045o3.md; STOP for approval. Known: FA 2026 (egazette 2026/271439) s.81 inserts s.354A, ss.100/102 omit ss.443/447; its 2025-Act part is pp.42–64 — read it in full. Mechanism exists: "omitted" in Act lists (D-067). (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw clean test -pl backend -Punit → 1488 pass, 0 fail, 0 Checkstyle violations (W-045o2)
Waiting on      : nothing for W-045o3; owner to itemise the backlog gaps (STATE); D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-045o2: register every in-force section of the Income-tax Act 1961 — rebuilt from the 2019 arrangement and every amending Act since (D-067)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
