# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-088c DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-088d (capital-loss carry-forward, S2 s.111 / 1961 s.74), then W-088e, W-088f (OQ-31), W-088g, W-088h, W-089. Open OQs: OQ-31, OQ-32, OQ-33
Last verify     : unit -Punit (clean) → 2983 run, 0 fail, 0 Checkstyle; integration 252/0 (W-088c, 2026-10-18)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-088c: limit the surcharge on the tax on dividends and special-rate gains to 15% and leave those gains out of the 25%/37% test — the dividends' share pro rata on aggregate income, OQ-30 answered by the Act (D-245, D-246)
Updated         : 2026-10-18, session 21 — W-088c committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
