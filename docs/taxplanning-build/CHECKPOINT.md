# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-088a DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-088b (special-rate tax: S2 ss.196–198 / 1961 ss.111A, 112, 112A rows, the ₹1,25,000, the basic-exemption shift, Chapter VIII limited; lifts gti's special-rate refusal), then W-088c (OQ-30 first), W-088d, W-088e, W-088f (OQ-31). Open OQs: OQ-30, OQ-31, OQ-32. UNVERIFIED: 0. Unplaced proposals: none
Last verify     : unit -Punit → 2971 run, 0 fail, 0 Checkstyle; integration 252/0 (W-088a, 2026-10-18; orb start + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-088a: compute the capital-gains head per rate basis and set losses off within it and into it from other heads — the order a declared default, OQ-32 raised; W-088 split into W-088a–f (D-242, D-243)
Updated         : 2026-10-18, session 21 — W-088a committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
