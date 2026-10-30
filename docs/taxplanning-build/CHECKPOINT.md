# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-199 — re-source statutory record rows 3 (STT Sl. 5, FA 2013) and 6 (SCRA s.2(h)(id)) to the Gazette (W-187 C4a, D-396) — NOT STARTED
Stage           : — (W-187 done, D-396; committed as the last item commit)
Plan            : — (write plans/W-199.md on "start W-199")
Approved        : —
Steps done      : —
Next step       : on "start W-199": check STATE line 60 board order first (R4: W-187 D  W-199 ·  W-188 ·); READ the W-199 row and the statutory record rows 3, 6; ONLY Gazette copies count as READ unless the owner names a source trustworthy; write the plan; STOP for approval. Every item commit: board + WL-A-2 if the runbook changed
Last verify     : W-187 — charge tests 168/0; unit 3811/0; integration 312/0 (2026-10-30, after clean)
Waiting on      : the owner — "start W-199" (next on the board); OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-187: re-source the W-177 / W-178 figures to the Gazette and NSE's circulars — IPFT rolled back to ₹0.01 per crore from 1-3-2026, not folded in; W-185 rewritten, W-199 placed (D-396)
Updated         : 2026-10-30 ~22:05, session 40 — W-187 done (D-396), W-199 placed next
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
