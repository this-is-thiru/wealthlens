# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-187 — re-source W-177 / W-178 to the Gazette (D-394, placed first by D-395) — NOT STARTED
Stage           : — (session 38 PAUSED; all committed and pushed)
Plan            : — (write plans/W-187.md on "start W-187")
Approved        : —
Steps done      : —
Next step       : on "start W-187": check STATE line 60 board order first (R4); READ the W-187 row + both 2026-10-28 sources records; ONLY official Gazette copies count as READ unless the owner names a source trustworthy — ask the owner which issuers (NSE circulars, SEBI's site, broker pages) count; write plans/W-187.md, STOP for approval. Every item commit: board + WL-A-2 if the runbook changed
Last verify     : W-178 — charge tests 168/0; unit 3811/0; integration 312/0; CI success on c515c558
Waiting on      : the owner — "start W-187"; OQ-32, OQ-33, OQ-34 open (every other finding is now a placed row, D-395)
Files touched   : (none)
Last item commit: taxplanning W-178: add equity futures and options cards for all three brokers and the law — the new STT from 30-3-2026, Finance Act 2026's assent; W-185 and W-186 placed (D-393)
Updated         : 2026-10-28, session 38 — PAUSED; order W-187 → W-188 … W-198 → W-185 → W-186 → W-179 → W-181 … W-184 (D-395); audit in reviews/2026-10-28-bookkeeping-audit.md
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
