# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-185 — BUILT, not committed (the IPFT line on the thirteen 2026-03 cards)
Stage           : 6 of 6 done; awaiting the owner's commit
Plan            : plans/W-185.md — built C1a, C2a, C3a, C4b
Approved        : "W-185 approved C1a C2a C3a C4b" (2026-10-31)
Steps done      : both; D-415 recorded in STATE, Built note in the WI row
Next step       : on the owner's "commit it", stage exactly the files under "Files touched" and
                  commit; then start W-186 (clearing charges) from a fresh resume check
Last verify     : targeted 170/0, unit 3822/0, integration 319/0 (2026-10-31); failing first 8,
                  bite probe bit and was reverted; no Java, no relock, no dump
Waiting on      : the owner — the commit; whether the resume check's grep should change (a
                  docs-only state line returns in place of the item commit); OQ-32, OQ-33, OQ-34
Files touched   : 13 cards under backend/src/main/resources/data/charges/*-2026-03-01.json and
                  zerodha-equity-delivery-2026-06-01.json; 7 edited + 1 new (…-sell-1cr-on-the-stt-
                  change.json) golden fixtures; docs/charges-engine/ac2-rate-verification.md;
                  docs/charges-engine/staging-runbook.md; 03-WORK-ITEMS.md; STATE.md; plans/W-185.md
Last item commit: taxplanning W-198: count a set's basis row as reached and drop the two one-row regime sets — their rows are reached through the set resting on them, not by a node, so the sets existed only for an overstated rule (D-414)
Updated         : 2026-10-31, session 43 — W-185 built (D-415), all green, awaiting the commit
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* after a docs-only post-commit state line, the grep returns that state-line commit
(`9dac3513`, `47b099d6` for W-198) rather than the item commit, because the state lines carry the item prefix
too. Compare the **item** commit named above; the tree, not the grep, is what decides. (Raised for a row in
`plans/W-185.md`, Risks.)
