# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-087b DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-088 (CapitalGainsNode — owns OQ-26 C1's pro-rata 15% limit, OQ-30, OQ-31, S2 s.111 carry-forward), then W-089, W-089a, W-089b. Open OQs: OQ-30, OQ-31 (both W-088). UNVERIFIED: 0. Unplaced proposals: none
Last verify     : unit -Punit → 2959 run, 0 fail, 0 Checkstyle; integration 252/0 (W-087b, 2026-10-18; DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-087b: map portfolio's capital gains into CapitalGainsInput — term and rate basis from the STT conditions of S2 ss.196–198 and 1961 ss.111A, 112, 112A; unclassified and speculative gains refused, OQ-31 raised (D-241)
Updated         : 2026-10-18, session 21 — W-087b committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
