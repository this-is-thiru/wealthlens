# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-078j2 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-078j3 (Rules 1962 r.2A, 2B, 3B, 4, 26C/12BB, 21AGA/10-IEA, the Rules' own notification S.O. 969(E) 26-3-1962) → W-078j4 → W-078j5 → W-078k → W-079 … Open sourcing gap: S.O. 3245(E) 18-12-2009 + S.O. 66(E) 12-1-2010 Gazette copies (33 r.3 versions incl. SAL-SALARY-FOR-ACCOM; not on egazette). Golden blockers: SAL-SALARY-FOR-ACCOM, SAL-SALARY-FOR-HRA (j3), SAL-SALARY-FOR-GRATUITY (j4)
Last verify     : ./mvnw test -pl backend -Punit → 2591 run, 0 fail, BUILD SUCCESS, 0 Checkstyle violations (W-078j2, 2026-10-10)
Waiting on      : the owner — next pick. Open OQs: none
Files touched   : —
Last item commit: taxplanning W-078j2: read rule 3's amendments in the Gazette and mark the six r.3(1) TY 2025-26 versions SOURCE_CHECKED (D-171)
Updated         : 2026-10-10, session 16
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
