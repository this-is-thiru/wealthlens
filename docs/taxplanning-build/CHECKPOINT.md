# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-052 — NetPayableNode
Stage           : NOT_STARTED
Plan            : — (plans/W-052.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-052 section of 03-WORK-ITEMS.md (requires tax.cess only in Phase 5; credits edge later; Appendix D on the credits edge) and only the sections it names; write plans/W-052.md with the D-043 standing-rules line; STOP for approval. Known: net payable = tax after rebate + (surcharge, W-056) + cess − (credits, Phase 9) — the node must also read tax.afterrebate, since tax.cess is the cess alone (W-051); verify with DR 08 T1-N1 and T1-N3 final figures
Last verify     : ./mvnw clean test -pl backend -Punit → 1574 pass, 0 fail, 0 Checkstyle violations (W-051)
Waiting on      : nothing for W-052; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-051: add CessNode and the Health and Education Cess row — 4% of the tax after rebate, never before it (D-079)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
