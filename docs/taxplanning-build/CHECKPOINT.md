# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-087 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-087b (CapitalGainsAdapter — source first: S2 ss.196–198, 1961 ss.111A, 112, 112A; RateBasisHint exposure; SPECULATIVE / NOT_CAPITAL_GAINS / UNCLASSIFIED), then W-088, W-089, W-089a. Open OQs: OQ-30 (W-088). UNVERIFIED: 0. Unplaced proposals: none
Last verify     : unit -Punit → 2946 run, 0 fail, 0 Checkstyle (W-087, 2026-10-18); integration 252/0 (W-086a; DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-087: add MoneyBridge, the one-way seam from canonical double rupees to exact Money — a non-canonical double is refused, not rounded; the adapter split to W-087b (D-240)
Updated         : 2026-10-18, session 21 — W-087 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
