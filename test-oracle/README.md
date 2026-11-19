# test-oracle — the independent oracle (DR 19 E4.2; W-141)

`oracle.py` reckons the income-tax payable on a total income — slabs, rebate and its relief, surcharge and its relief,
cess, rounding — for both regimes, the old regime's three age classes, and TY 2025-26 and 2026-27. It is written from the
Gazette text recorded in `docs/taxplanning-build/sources/2026-11-07-boundary-figures.md`, with every figure hard-coded.
`backend`'s `OracleAgreementTest` requires the engine to pay exactly what `expected.csv` says on every row.

## Regenerate (from the repository root)

```
python3 test-oracle/cases.py     # writes cases.csv: the fixed grid of incomes
python3 test-oracle/oracle.py    # reproduces the 44 hand-worked T1/T2 cases first; writes expected.csv only if all match
git diff test-oracle/            # read what changed
```

`expected.csv`'s first line is the sha256 of `oracle.py` and `cases.csv`; the Java test fails if either changed without
regenerating, since the build itself has no Python.

## What is independent, and what is not

- **Independent:** no code, rule pack or Java of the engine's; its figures read from the Gazette, not from the packs.
- **Not independent:** the same author as most of the engine (W-141 C1a). A misreading of the statute common to both
  would pass both. The 44 hand-worked cases, each derived and shown by hand, are the check on that.
- **Read for TY 2026-27 from the Gazette in full; for TY 2025-26 partly:** the new-regime rebate's relief wording is the
  2025 Act s.156(2)'s; for 2025-26 the repo has read only FA 2025 s.20, which substitutes figures into the 1961 s.87A
  proviso (inserted by FA 2023, whose Gazette text has not been read). The old regime's s.87A and s.288B for 2025-26
  rest on the thc.nic.in consolidation, as the T1-O cases do.

## When the engine and the oracle differ

The test lists every differing row with both figures. One of the two is wrong: work the row by hand, then fix whichever
is wrong. Never regenerate `expected.csv` to match the engine.
