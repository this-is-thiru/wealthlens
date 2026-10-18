# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-086 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-086a (one financial-year form in trade_outcomes — TradeMatchingService and a migration of the FY… rows), then W-087 (MoneyBridge + CapitalGainsAdapter), W-088, W-089. Open OQs: OQ-30 (W-088). UNVERIFIED: 0. Unplaced proposals: none
Last verify     : unit -Punit → 2939 run, 0 fail, 0 Checkstyle; integration 248/0 (W-086, 2026-10-18; DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-086: publish portfolio's capital-gains read-model from trade_outcomes — gains net of the deductible charges, not netProfit, and a charged row without a breakdown refuses the year (D-238)
Updated         : 2026-10-18, session 21 — W-086 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
