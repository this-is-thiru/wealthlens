# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-085 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-086 (portfolio publishes the read-model, built from trade_outcomes — D-237), then W-087, W-088, W-089. Open OQs: OQ-30 (W-088). UNVERIFIED: 0. Unplaced proposals: none
Last verify     : unit -Punit → 2929 run, 0 fail, 0 Checkstyle (W-085, 2026-10-18)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-085: declare portfolio's capital-gains read-model — gross per classification, asset type and sub-class, no brought-forward loss, which is the tax ledger's (D-237)
Updated         : 2026-10-18, session 21 — W-085 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
