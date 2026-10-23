# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-154d — expense benefits and clubs by rule data (D-325; Q6, Q7)
Stage           : READ — W-154c committed; plan for W-154d being written
Plan            : plans/W-154d.md (to write)
Approved        : —
Steps done      : —
Next step       : read W-154d WI row, plans/W-154.md Q6 – Q7, ExpenseBenefitPerquisite, EmployerExpenseBenefit, the PERQ-HOLIDAY/-CREDIT-CARD/-CLUB*/-OTHER-BENEFIT rows, ExpenseAndAssetPerquisiteTest; write plans/W-154d.md; STOP
Last verify     : targeted 219/0; unit -Punit 3537/0; integration 281/0 (2026-10-23, W-154c)
Waiting on      : — (writing W-154d plan); OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-154c: choose how much of a meal is salary as rule data — RS-PERQ-MEAL-EXCLUDED (new PERQ-MEAL-TEA-SNACKS, PERQ-MEAL-REMOTE) and RS-PERQ-MEAL-LIMIT by meals.setting, else PERQ-MEAL in full, so MealPerquisite holds no setting switch (D-328)
Updated         : 2026-10-23, session 30 — W-154c done and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
