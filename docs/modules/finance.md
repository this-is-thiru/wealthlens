# Module `finance` — loan and SIP calculators

> W-357b1 (D-901), written from the code at `review/taxplanning` on 2026-11-22. Every claim cites the line it was read from; a claim this
> doc cannot cite is not made. Template: `docs/taxplanning-build/plans/W-357b.md` C2a.

## 1. Purpose

Two stateless calculators for a person planning a loan or a SIP. The first works out a loan's EMI, total repaid, principal or term from the
other figures. The second finds the return a step-up SIP needs to reach a target. Nothing is stored and nobody need sign in: they are public
arithmetic, kept out of the portfolio and tax modules because they read none of a user's data.

## 2. Boundaries

- `OPEN` module, `allowedDependencies = {"shared"}` (`backend/src/main/java/com/thiru/wealthlens/finance/package-info.java:1` – `:5`).
- It uses only `shared/web/PublicApi` (`FinancesController.java:9`, `:16`). No other module imports from `finance`.
- URL segment `finances` (`shared/web/ApiZones.java:26` – `:27`, the `MODULE_PATHS` map).

## 3. Routes

Both are in the **public** zone (`@PublicApi`, `FinancesController.java:16`): no token, and any `Authorization` header is ignored. Both
bodies are `@Valid` (`FinancesController.java:25`, `:30`). Errors are `ProblemDetail`; a refusal is logged at WARN (W-370).

| Method and path | Request → answer | Where |
|---|---|---|
| `POST /api/v1/public/finances/calculate` | `FinanceRequest` → `FinanceResponse` | `FinancesController.java:24` – `:27` |
| `POST /api/v1/public/finances/interest-rate` | `StepUpSIPRequest` → `InterestRateResponse` | `FinancesController.java:29` – `:32` |

**`FinanceRequest`** (`finance/dto/FinanceRequest.java:12` – `:19`):
- `calculationType` is required (`@NotNull`, `:13` – `:14`): one of `EMI`, `TOTAL_AMOUNT`, `PRINCIPAL_AMOUNT`, `RATE_OF_INTEREST`,
  `TIME_PERIOD`, `NONE` (`dto/enums/CalculationType.java:3` – `:10`).
- `principalAmount`, `rateOfInterest` (a **percent a year**: 12 means 12%), `timePeriod` (months), `finalAmount` and `emiAmount`
  (`:15` – `:19`). They are primitives, bound through the all-args constructor, so a missing one is refused: "Field 'x' must be a number"
  (W-365's build, D-889).

**`FinanceResponse`** (`dto/FinanceResponse.java:12` – `:17`): `principalAmount`, `rateOfInterest`, `timePeriod`, `finalAmount` and
`emiAmount` (a whole number). Only the figure asked for is filled; the rest are 0.

**`StepUpSIPRequest`** (`dto/StepUpSIPRequest.java:12` – `:18`):
- `initialAmount`, the first month's SIP.
- `stepUpRate`, a **fraction per step**: 0.10 means 10%. This is a different scale from `rateOfInterest` above
  (`StepUpSIPCalculator.java:103` multiplies by `1 + stepUpRate`).
- `stepUpFrequency`, required (`@NotNull`, `:15` – `:16`): `MONTHLY` (1), `QUARTERLY` (3), `HALF_YEARLY` (6) or `YEARLY` (12) months
  (`dto/enums/StepUpFrequency.java:7` – `:10`).
- `months` and `targetAmount`.

**`InterestRateResponse`** (`dto/InterestRateResponse.java:10` – `:12`): `monthlyRate` and `annualRate`, both percents with two
decimals.

**Refusals (400):**

| When | Message | Where |
|---|---|---|
| `calculationType` missing | "Field 'calculationType' must not be null" | `FinanceRequest.java:13`; `CallerMistakeIntegrationTest` |
| `stepUpFrequency` missing | "Field 'stepUpFrequency' must not be null" | `StepUpSIPRequest.java:15`; `CallerMistakeIntegrationTest` |
| `calculationType` `NONE` | "Invalid calculation type" | `FinancesService.java:22` |
| EMI or total: principal < 0, rate < 0, or term ≤ 0 | "Principal amount must be non-negative", "Rate of interest must be non-negative", "Time period must be greater than zero" | `FinancesService.java:95` – `:105` |
| principal: EMI ≤ 0, rate < 0, or term ≤ 0 | "EMI amount must be greater than zero", and the two above | `FinancesService.java:106` – `:116` |
| term: principal ≤ 0, EMI ≤ 0, rate < 0, or EMI ≤ principal × monthly rate (the loan never ends) | "Principal amount must be greater than zero", "EMI amount must be greater than zero", "Rate of interest must be non-negative", "EMI must be greater than principal times monthly rate" | `FinancesService.java:117` – `:130` |

## 4. Stored data

None. The package has no `@Document`, and neither service holds a repository.

## 5. Behaviour

Monthly rate r = `rateOfInterest` / (12 × 100) everywhere (`FinancesService.java:82`).

| Type | What is computed | Where |
|---|---|---|
| `EMI` | P·r·(1+r)^n / ((1+r)^n − 1); with r = 0, P / n. **Rounded up** to a whole rupee | `FinancesService.java:26` – `:31`, `:79` – `:91` |
| `TOTAL_AMOUNT` | the unrounded EMI × n | `FinancesService.java:33` – `:37` |
| `PRINCIPAL_AMOUNT` | EMI·((1+r)^n − 1) / (r·(1+r)^n); with r = 0, EMI × n. Rounded to the paisa | `FinancesService.java:39` – `:57` |
| `TIME_PERIOD` | n = ln(EMI / (EMI − P·r)) / ln(1 + r); with r = 0, P / EMI. **Rounded up** to whole months | `FinancesService.java:59` – `:77` |
| `RATE_OF_INTEREST` | the given `rateOfInterest` back unchanged: no rate is solved for | `FinancesService.java:19` – `:20` |

**Step-up SIP** (`StepUpSIPCalculator.java:12` – `:111`):
- **Future value of a monthly rate m.** Each month's SIP grows for the months left, including its own. The SIP steps up by `stepUpRate` at the
  start of each period of `stepUpFrequency` months, from the second period on (`:95` – `:111`).
- **Solving for m.** Newton–Raphson from 0.01, or from −0.05 when the target is below the total paid in (`:16` – `:17`). The derivative is
  taken over a 0.0001 step (`:88` – `:93`). It stops when two guesses differ by less than 0.00001, or after 100 iterations (`:13` – `:14`,
  `:73` – `:86`).
- **The answer.** The monthly rate and (1 + m)^12 − 1, each as a percent with two decimals (`:20` – `:26`).

### Worked examples (by hand)

**EMI**, P = ₹1,00,000 at 12% for 12 months:
1. r = 12 / 1200 = 0.01.
2. (1.01)^12 = 1.12682503.
3. EMI = 1,00,000 × 0.01 × 1.12682503 / 0.12682503 = 1,126.82503 / 0.12682503 = 8,884.88.
4. Rounded up: **₹8,885**. This is `FinancesServiceTest.calculateEmi_when1Lac12Percent12Mo_returns8885` (`FinancesServiceTest.java:21`).

**Step-up SIP**, ₹1,000 a month for 2 months, no step-up (`stepUpRate` 0, `MONTHLY`), target ₹2,030.10:
1. FV(m) = 1,000(1+m)^2 + 1,000(1+m).
2. At m = 0.01: 1,020.10 + 1,010.00 = 2,030.10, which is the target.
3. So the answer is `monthlyRate` **1.00** and `annualRate` (1.01^12 − 1) × 100 = 12.68 → **12.68**.

No test asserts this case: see gap 1.

## 6. Configuration

None: no `@ConfigurationProperties` and no `app.*` key is read in the package.

## 7. Tests

| Class | What it covers |
|---|---|
| `backend/src/test/java/com/thiru/wealthlens/service/FinancesServiceTest.java` (12) | each calculation type with and without interest, and the `NONE` and too-low-EMI refusals |
| `backend/src/test/java/com/thiru/wealthlens/integration/FinancesIntegrationTest.java` (5) | `/calculate` through the app (`:53`, `:80`, `:106`, `:130`, `:155`) |
| `CallerMistakeIntegrationTest` (2 cases) | the two required fields (W-365) |

`StepUpSIPCalculator`'s figures have no test.

## 8. Known gaps (placed as W-371, D-901)

1. **The step-up SIP's figures are untested.** Only its refusal for a missing frequency is covered.
2. **`calculateMonthlyRate` is dead.** It is a private method nothing calls (`StepUpSIPCalculator.java:29` – `:49`).
3. **The SIP's inputs are not checked.** `months` 0, `targetAmount` 0 or a negative `initialAmount` reach the iteration. With `months` 0 the
   future value and its derivative are both 0, so the step at `:78` divides by a zero derivative.
4. **A solve that does not converge is answered, not refused.** After 100 iterations the last guess is returned as the rate (`:85`).
5. **The api-collection's `Finance/Sip.yml` sends `"stepUpFrequency": "1"`, not a name.** Its `docs` block shows `"MONTHLY"` and
   `stepUpRate` 10. How a numeric string binds to the enum is **not verified** here, and 10 would be 1,000% a step on this scale.

## 9. Not covered here

- How a zone and module prefix are added to a path, and the error format: `shared` (W-358c1, W-359a).
- Request validation in general: `plans/W-358d4.md`.
