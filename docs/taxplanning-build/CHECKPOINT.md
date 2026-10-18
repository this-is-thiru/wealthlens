# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-088b DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick, and ask placement of the two limbs (S2 s.197(3) / 1961 s.112(1)(a) second proviso land-or-building; s.197(4) / s.112(1)(c)(iii) non-resident unlisted). Next in order: W-088c (OQ-30 first), W-088d, W-088e, W-088f (OQ-31). Open OQs: OQ-30 – OQ-33
Last verify     : unit -Punit (clean) → 2975 run, 0 fail, 0 Checkstyle; integration 252/0 (W-088b, 2026-10-18)
Waiting on      : the owner — next pick; placement of two limbs
Files touched   : none
Last item commit: taxplanning W-088b: tax the special-rate capital gains at their own rates, with the unused basic exemption shifted onto them and Chapter VIII kept off them — the shift order the Department's utility's, OQ-33 raised (D-244)
Updated         : 2026-10-18, session 21 — W-088b committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
