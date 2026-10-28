# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-178 — equity futures and options (D-383; coverage G4) — NOT STARTED
Stage           : — (W-177 DONE, D-392, committed)
Plan            : — (write plans/W-178.md on "start W-178")
Approved        : —
Steps done      : —
Next step       : on "start W-178": READ the WI row + coverage G4 + the brokers' F&O pages + the STT change of 1-4-2026 (source the Act, as W-163c), write plans/W-178.md, STOP for approval
Last verify     : W-177 — golden 31/0; unit 3802/0; integration 312/0
Waiting on      : the owner — "start W-178"; push (parent + api-collection) if wanted; open: CLAUDE.md "backfill" 503 wording; catalogue SEBI_FEE reference; api-collection term requests; per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-177: add the Upstox and Fyers intraday, mutual fund and maintenance cards, each figure read on the broker's page — uncarded Upstox and Fyers trades now resolve NO_MATCHING_RULES (D-392)
Updated         : 2026-10-28, session 38 — W-164 – W-167, W-177 DONE; W-180 closed; W-181 – W-184 placed (D-391)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
