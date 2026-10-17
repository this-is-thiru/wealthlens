# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j7b3 DONE; Phase 8 complete; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner's pick. Next in order: W-081 (set-off; also runs DR 08 T6-2, T6-4, T6-5 and T6-3's TY 2026-27 half, D-211). UNVERIFIED: 0 (W-078j7c, after W-089d, watches r.280). Open OQs: OQ-25, OQ-26, OQ-27. Unplaced proposals: D-210 C2(b) (PackAssembler takes the instruments); W-080's SalaryRun rename
Last verify     : unit -Punit → 2836 run, 0 fail, 0 Checkstyle (W-078j7b3, 2026-10-17)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-078j7b3: source-check r.280's two TY 2026-27 allowance versions on the owner's ruling of its defective text — no UNVERIFIED rule version remains (D-216)
Updated         : 2026-10-17, session 20 (W-078j7b3 committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
