# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : session 110 paused (owner: session limit, Mac shutting down). All committed and pushed; dev wiped (0 collections)
Stage           : nothing in flight — W-334c7a – c7c D, W-350 D; W-353 placed (·), not planned
Done session 110: D-821 – D-827: 61 refused rows left (years filed); W-334c7b2 owner's rebuild; W-334c7b3 test account removed; W-334c7c signed off + wipe; other two users all delivery; W-353 exchange check (EQUITY / MF: NSE or BSE only, before W-334e); W-337 = MySQL on Oracle Always Free HeatWave, before M2
Board           : W-353 ·, W-348a · / W-348b · (the other two users, declared delivery), W-351 ·, W-334d ·, W-334e ·, W-337 ·
Next step       : on 'resume': resume check; then ask the owner's yes to W-353's table for the other asset types (BOND / GOLD_BOND: NSE, BSE, OFF_EXCHANGE; FD / INSURANCE: OFF_EXCHANGE only; no exchange → refused); then plan W-353 (read prod's BMF transactions' asset_type first); then the other two users' W-348a / W-348b on a fresh copy; then W-334d (staging)
Open to owner   : W-353's table (above); 13 BMF rows / §3.5 2 / 49 close with W-353 + the other users' rebuild
Env notes       : jars in ../wealthlens-m1.3 and ../wealthlens-m0; ~/wealthlens-runs/w334c holds prod-before-m1.archive (mode 600), prod-reference.txt, allotment-dates.json and the query scripts only; zsh: wrap globs in (setopt null_glob; …); DNS timeouts — 3-try loops; never cd inside a $ command
Last verify     : no code changed this session; scripts tried on throwaway mongo:8.0.4 (results in the plans)
Last item commit: taxplanning W-334c7c: sign off the prod rehearsal on m1.3 and wipe dev — §3 as expected on prod's copy, the rehearsal files deleted, prod's archive kept for W-334e
Files touched   : none
Updated         : 2026-11-20, session 110 (paused)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* once a post-commit state line exists, the grep returns **that** commit rather than the
item commit named above, because state lines carry the item prefix too. Compare the **item** commit; the tree,
not the grep, is what decides. It has fired for W-179's planning (`f461363c` against `866f3dd2`), W-181's
(`c620ba99` against `c76dad9a`), W-182's (`592ff6a6` against `e494326f`), W-183's (`ac3a9fce` against
`ecf681cd`), W-184's (`235d2566` against `f511544a`, then `a0fe2665`), W-088g's (`c59d8223` against
`c030e04f`), W-088g1's (`1f975262` against `f3027724`), W-088g2's (`09ed07b7` against `927b2581`) and
W-089's (`f7140cfe` against the post-commit line `1b62fe5a`); W-131d2's (`d7a5b146` pointer against `86035a1c`).
