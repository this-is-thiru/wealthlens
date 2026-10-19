# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (between items)
Stage           : W-088e done (D-248); next in order W-088f (waits on OQ-31)
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's next instruction ("take W-088f" or another order)
Last verify     : unit -Punit (clean) → 2989 run, 0 fail, 0 Checkstyle; integration 252/0 (W-088d, 2026-10-18)
Waiting on      : the owner — next item; OQ-31 – OQ-33 open
Files touched   : —
Last item commit: taxplanning W-088e: close out the capital-gains chapter's coverage — S2 s.67 / 1961 s.45 modelled with a catalogue entry, s.86 / s.54F to W-088i, the rest deferred, 1961 ss.54E–54EE spent (D-248)
Updated         : 2026-10-19, session 22 — W-088e committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
