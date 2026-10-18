# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-088d DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-088e (the capital-gains chapter's coverage close-out, 57 pointers, content only), then W-088f (OQ-31), W-088g, W-088h, W-089. Open OQs: OQ-31, OQ-32, OQ-33
Last verify     : unit -Punit (clean) → 2989 run, 0 fail, 0 Checkstyle; integration 252/0 (W-088d, 2026-10-18)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-088d: carry a capital loss forward eight years from the tax ledger, long-term only against long-term gains — the input's brought-forward field removed, the order declared (D-247)
Updated         : 2026-10-18, session 21 — W-088d committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
