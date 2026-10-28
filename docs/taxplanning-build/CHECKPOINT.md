# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-185 — IPFT counted twice on the 2026-03 cash cards — NOT STARTED; but FIRST ask the owner to place W-187 (re-source W-177 / W-178 to the Gazette; proposed before W-185, which rests on a non-Gazette figure)
Stage           : — (session 38 PAUSED at the owner's session-limit notice; all committed and pushed)
Plan            : — (write plans/W-185.md on "start W-185")
Approved        : —
Steps done      : —
Next step       : ASK the owner where W-187 goes (D-394). Then, for whichever item is first: check STATE line 60 board order (R4) BEFORE starting; only official Gazette copies count as READ unless the owner names a source trustworthy; update the board + WL-A-2 (if the runbook changes) in every item commit
Last verify     : W-178 — charge tests 168/0; unit 3811/0; integration 312/0
Waiting on      : the owner — W-187 placement; then "start <item>"; open: CLAUDE.md "backfill" 503 wording; catalogue SEBI_FEE reference; api-collection term requests; per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-178: add equity futures and options cards for all three brokers and the law — the new STT from 30-3-2026, Finance Act 2026's assent; W-185 and W-186 placed (D-393)
Updated         : 2026-10-28, session 38 — PAUSED; owner correction recorded (D-394): board fixed, WL-A-2 republished, R4 deviation recorded, W-187 written unplaced; all committed and pushed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
