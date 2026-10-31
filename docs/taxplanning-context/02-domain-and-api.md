# 02 — Domain model, API surface, configuration, inputs and outputs

All paths are relative to `backend/src/main/java/com/thiru/wealthlens/` unless stated otherwise.

---

## 1. Enums — verbatim

```java
// taxplanning/enums/AllowanceCategory.java:3-8
public enum AllowanceCategory {
    ALLOWANCE,
    PERQUISITE,
    RETIREMENT,
    DEDUCTION
}
```

```java
// taxplanning/enums/AvailabilityPath.java:3-8
public enum AvailabilityPath {
    HR_RESTRUCTURE,
    SELF_DECLARE_ITR,
    BOTH,
    NOT_APPLICABLE
}
```

```java
// taxplanning/enums/CarEngineSize.java:3-6
public enum CarEngineSize {
    LEQ_1600CC_OR_EV,
    GT_1600CC
}
```

```java
// taxplanning/enums/CarOwnership.java:3-7
public enum CarOwnership {
    EMPLOYER_OWNED,
    EMPLOYEE_OWNED,
    LEASED
}
```

```java
// taxplanning/enums/CityTier.java:3-6
public enum CityTier {
    METRO_8,
    NON_METRO
}
```

```java
// taxplanning/enums/EmployerType.java:3-7
public enum EmployerType {
    GOVT,
    PSU,
    PRIVATE
}
```

```java
// taxplanning/enums/HrSupportLikelihood.java:3-9
public enum HrSupportLikelihood {
    HIGH,
    MEDIUM,
    LOW,
    EMPLOYER_DISCRETION,
    NOT_APPLICABLE
}
```

```java
// taxplanning/enums/ProfileType.java:3-8
public enum ProfileType {
    OFFER_LETTER,
    CURRENT,
    RESTRUCTURED,
    TEMPLATE
}
```

```java
// taxplanning/enums/RegimeType.java:3-7
public enum RegimeType {
    NEW_REGIME,
    OLD_REGIME,
    NOT_DECIDED
}
```

Shared enum used on every entity in the module:

```java
// shared/dto/enums/EntityStatus.java:3-7
public enum EntityStatus {
    ACTIVE,
    INACTIVE,
    SUPERSEDED
}
```

`CarOwnership.LEASED` is never branched on: `PerquisiteValuationService.computeCarPerquisiteMonthly`
tests only `== CarOwnership.EMPLOYEE_OWNED` (`taxplanning/engine/PerquisiteValuationService.java:102`),
so `LEASED` falls through to the `EMPLOYER_OWNED` rates. `CityTier` is stored
(`SalaryProfileEntity.java:45-46`) but never read by any engine — metro status comes from
`isMetroCity`, derived from `cityName` against a hardcoded list (see §2.1).

---

## 2. Entities and collections

### 2.1 `salary_profiles` — `taxplanning/salary/entity/SalaryProfileEntity.java:21`

`@Document(value = "salary_profiles")`, implements `AuditableEntity`. **No `@Indexed` on any field.**

| Field | Java type | Mongo field | Line |
|---|---|---|---|
| `id` | `String` | `_id` (`@Id`) | 27-28 |
| `email` | `String` | `email` | 30-31 |
| `profileName` | `String` | `profile_name` | 33-34 |
| `profileType` | `ProfileType` | `profile_type` (STRING) | 36-37 |
| `employerName` | `String` | `employer_name` | 39-40 |
| `taxYear` | `String` | `tax_year` | 42-43 |
| `cityTier` | `CityTier` | `city_tier` (STRING) | 45-46 |
| `cityName` | `String` | `city_name` | 48-49 |
| `regimeType` | `RegimeType` | `regime_type` (STRING) | 51-52 |
| `components` | `List<SalaryComponentEntity>` | `components` | 54-55 |
| `employerType` | `EmployerType` | `employer_type` (STRING) | 57-58 |
| `carProvided` | `Boolean` | `car_provided` | 60-61 |
| `carOwnership` | `CarOwnership` | `car_ownership` (STRING) | 63-64 |
| `carEngineSize` | `CarEngineSize` | `car_engine_size` (STRING) | 66-67 |
| `driverProvidedByEmployer` | `Boolean` | `driver_provided_by_employer` | 69-70 |
| `driverSalaryMonthly` | `Long` | `driver_salary_monthly` | 72-73 |
| `investment80c` | `Long` | `investment_80c` | 75-76 |
| `investment80d` | `Long` | `investment_80d` | 78-79 |
| `homeLoanInterest` | `Long` | `home_loan_interest` | 81-82 |
| `npsSelf80ccd1b` | `Long` | `nps_self_80ccd1b` | 84-85 |
| `monthlyRentPaid` | `Long` | `monthly_rent_paid` | 87-88 |
| `isPayingRent` | `Boolean` | `is_paying_rent` | 90-91 |
| `isMetroCity` | `Boolean` | `is_metro_city` | 93-94 |
| `numberOfChildren` | `Integer` | `number_of_children` | 96-97 |
| `isPhysicallyDisabled` | `Boolean` | `is_physically_disabled` | 99-100 |
| `ltaBlockStart` | `String` | `lta_block_start` | 102-103 |
| `ltaTripsClaimedInBlock` | `Integer` | `lta_trips_claimed_in_block` | 105-106 |
| `ltaCarryForwardAvailable` | `Boolean` | `lta_carry_forward_available` | 108-109 |
| `esopPresent` | `Boolean` | `esop_present` | 111-112 |
| `sourceText` | `String` | `source_text` | 114-115 |
| `parsedByAi` | `Boolean` | `parsed_by_ai` | 117-118 |
| `status` | `EntityStatus` | `status` (STRING) | 120-121 |
| `auditMetadata` | `AuditMetadata` | `audit_metadata` (setter suppressed) | 123-125 |

**Six fields exist on the entity but not on `SalaryProfileRequest`**: `id`, `status`, `isMetroCity`,
`esopPresent`, `sourceText`, `parsedByAi`. Of these, `isMetroCity` and `status` are set by the
service (`salary/service/SalaryProfileService.java:70,76,125`). `esopPresent`, `sourceText` and
`parsedByAi` have **no production writer anywhere** — grep for `setEsopPresent|setSourceText|
setParsedByAi` across `backend/src` finds one hit, in a test
(`NewRegimeTaxEngineTest.java:290`). Both engines branch on `esopPresent`
(`NewRegimeTaxEngine.java:168`, `OldRegimeTaxEngine.java:239`), so that warning can never fire in
production.

Embedded component (`taxplanning/salary/entity/SalaryComponentEntity.java:11-24`):

| Field | Type | Mongo field | Line |
|---|---|---|---|
| `allowanceCode` | `String` | `allowance_code` | 13-14 |
| `annualAmount` | `Long` | `annual_amount` | 16-17 |
| `isCurrent` | `Boolean` | `is_current` | 19-20 |
| `notes` | `String` | `notes` | 22-23 |

`isMetroCity` derivation — `salary/dto/SalaryProfileResponse.java:53-56,110-116`:

```java
private static final List<String> METRO_CITIES = Arrays.asList(
        "Mumbai", "Delhi", "Kolkata", "Chennai",
        "Hyderabad", "Bengaluru", "Pune", "Ahmedabad"
);

public static boolean deriveIsMetroCity(String cityName) {
    if (cityName == null || cityName.isBlank()) {
        return false;
    }
    return METRO_CITIES.stream()
            .anyMatch(metro -> metro.equalsIgnoreCase(cityName.trim()));
}
```

The identical list is duplicated at `taxplanning/engine/HraExemptionCalculator.java:16-19`, where it
is declared but never referenced.

### 2.2 `tax_computations` — `taxplanning/salary/entity/TaxComputationEntity.java:17`

| Field | Java type | Mongo field | Line |
|---|---|---|---|
| `id` | `String` | `_id` | 23-24 |
| `salaryProfileId` | `String` | `salary_profile_id` | 26-27 |
| `email` | `String` | `email` | 29-30 |
| `taxYear` | `String` | `tax_year` | 32-33 |
| `newRegimeResult` | `TaxResult` | `new_regime_result` | 35-36 |
| `oldRegimeResult` | `TaxResult` | `old_regime_result` | 38-39 |
| `recommendedRegime` | `RegimeType` | `recommended_regime` (STRING) | 41-42 |
| `annualSavingVsCurrent` | `Long` | `annual_saving_vs_current` | 44-45 |
| `capitalGainsResult` | `Object` | `capital_gains_result` | 47-48 |
| `status` | `EntityStatus` | `status` (STRING) | 50-51 |
| `auditMetadata` | `AuditMetadata` | `audit_metadata` | 53-55 |

`capitalGainsResult` is the module's only capital-gains surface. It is typed `Object`, and
`grep -rn "setCapitalGainsResult"` across `backend/src` returns nothing — **it is never written**.

Nested `TaxResult` (`:57-105`) — every amount is `Long` (whole rupees):

| Field | Type | Mongo field | Line |
|---|---|---|---|
| `grossSalary` | `Long` | `gross_salary` | 61-62 |
| `totalExemptions` | `Long` | `total_exemptions` | 64-65 |
| `totalDeductions` | `Long` | `total_deductions` | 67-68 |
| `taxableIncome` | `Long` | `taxable_income` | 70-71 |
| `basicTaxBeforeRebate` | `Long` | `basic_tax_before_rebate` | 73-74 |
| `rebate87aApplied` | `Long` | `rebate87a_applied` | 76-77 |
| `taxAfterRebate` | `Long` | `tax_after_rebate` | 79-80 |
| `marginalReliefApplied` | `Boolean` | `marginal_relief_applied` | 82-83 |
| `surcharge` | `Long` | `surcharge` | 85-86 |
| `cess` | `Long` | `cess` | 88-89 |
| `totalTax` | `Long` | `total_tax` | 91-92 |
| `netTakeHome` | `Long` | `net_take_home` | 94-95 |
| `appliedExemptions` | `Object` | `applied_exemptions` | 97-98 |
| `appliedDeductions` | `Object` | `applied_deductions` | 100-101 |
| `warnings` | `Object` | `warnings` | 103-104 |

The last three are typed `Object` but always assigned `ArrayList<String>`
(`engine/AbstractTaxEngine.java:105,107-108`).

### 2.3 `tax_slab_policies` — `taxplanning/policy/entity/TaxSlabPolicyEntity.java:18`

| Field | Type | Mongo field | Line |
|---|---|---|---|
| `id` | `String` | `_id` | 24-25 |
| `taxYear` | `String` | `tax_year` | 27-28 |
| `regimeType` | `RegimeType` | `regime_type` (STRING) | 30-31 |
| `status` | `EntityStatus` | `status` (STRING) | 33-34 |
| `sourceReference` | `String` | `source_reference` | 36-37 |
| `standardDeduction` | `Long` | `standard_deduction` | 39-40 |
| `rebate87aLimit` | `Long` | `rebate87a_limit` | 42-43 |
| `rebate87aAmount` | `Long` | `rebate87a_amount` | 45-46 |
| `basicExemptionLimit` | `Long` | `basic_exemption_limit` | 48-49 |
| `slabs` | `List<TaxSlab>` | `slabs` | 51-52 |
| `surchargeSlabs` | `List<SurchargeSlab>` | `surcharge_slabs` | 54-55 |
| `cessPercentage` | `Double` | `cess_percentage` | 57-58 |
| `auditMetadata` | `AuditMetadata` | `audit_metadata` | 60-62 |

`basicExemptionLimit` is stored and seeded but **never read by any engine** —
`grep "getBasicExemptionLimit"` finds no caller.

Nested `TaxSlab` (`:64-76`): `fromAmount` `Long`/`from_amount`, `toAmount` `Long`/`to_amount`,
`ratePercent` `Double`/`rate_percent`.

Nested `SurchargeSlab` (`:78-96`): `regimeType` `RegimeType`/`regime_type` (STRING), `fromAmount`,
`toAmount`, `ratePercent`, `marginalReliefApplicable` `Boolean`/`marginal_relief_applicable`.
`marginalReliefApplicable` is stored and seeded `true` on every row but **never read** —
`grep "getMarginalReliefApplicable"` finds no caller.

### 2.4 `perquisite_policies` — `taxplanning/policy/entity/PerquisitePolicyEntity.java:16`

| Field | Type | Mongo field | Line | Read by an engine? |
|---|---|---|---|---|
| `id` | `String` | `_id` | 22-23 | — |
| `taxYear` | `String` | `tax_year` | 25-26 | lookup key |
| `status` | `EntityStatus` | `status` (STRING) | 28-29 | lookup key |
| `sourceReference` | `String` | `source_reference` | 31-32 | no |
| `carLeq1600ccOrEvNoDriver` | `Long` | `car_leq1600cc_or_ev_no_driver` | 34-35 | yes — `PerquisiteValuationService.java:123` |
| `carLeq1600ccOrEvWithDriver` | `Long` | `car_leq1600cc_or_ev_with_driver` | 37-38 | yes — `:115` |
| `carGt1600ccNoDriver` | `Long` | `car_gt1600cc_no_driver` | 40-41 | yes — `:126` |
| `carGt1600ccWithDriver` | `Long` | `car_gt1600cc_with_driver` | 43-44 | yes — `:118` |
| `driverPerquisiteMonthly` | `Long` | `driver_perquisite_monthly` | 46-47 | yes — `AbstractTaxEngine.java:221` |
| `mealPerMealAmount` | `Long` | `meal_per_meal_amount` | 49-50 | yes — `PerquisiteValuationService.java:161` |
| `mealMealsPerDay` | `Integer` | `meal_meals_per_day` | 52-53 | yes — `:162` |
| `mealWorkingDaysPerMonth` | `Integer` | `meal_working_days_per_month` | 55-56 | yes — `:163` |
| `giftAnnualLimit` | `Long` | `gift_annual_limit` | 58-59 | yes — `:183` |
| `concessionalLoanExemptLimit` | `Long` | `concessional_loan_exempt_limit` | 61-62 | **no caller** |
| `accommodationMetroPercent` | `Double` | `accommodation_metro_percent` | 64-65 | **no caller** |
| `accommodationLargeCityPercent` | `Double` | `accommodation_large_city_percent` | 67-68 | **no caller** |
| `accommodationOtherPercent` | `Double` | `accommodation_other_percent` | 70-71 | **no caller** |
| `employerSchoolMonthlyPerChild` | `Long` | `employer_school_monthly_per_child` | 73-74 | **no caller** |
| `carDepreciationRatePercent` | `Double` | `car_depreciation_rate_percent` | 76-77 | **no caller** |
| `auditMetadata` | `AuditMetadata` | `audit_metadata` | 79-81 | — |

### 2.5 `allowance_catalogue` — `taxplanning/policy/entity/AllowanceCatalogueEntity.java:21`

Descriptive metadata plus HR-facing guidance. 28 fields, `:27-110`. No indexes declared.

`id`, `code`, `displayName`, `shortName`, `category` (`AllowanceCategory`, STRING), `taxYear`,
`regimeType` (`RegimeType`, STRING), `requiresBills`, `requiresRentReceipt`,
`isEmployerContribution`, `annualLimitFixed` (`Long`), `monthlyLimitFixed` (`Long`),
`limitFormula` (`String`), `itSection`, `itActReference`, `description`, `whyItMatters`,
`availabilityPath` (`AvailabilityPath`, STRING), `hrSupportLikelihood` (`HrSupportLikelihood`,
STRING), `howToAvailViaHr`, `hrAskTemplate`, `whatIfHrSaysNo`, `documentsRequired` (`List<String>`),
`documentsToKeep` (`List<String>`), `eligibilityConditions` (`List<String>`), `commonMistakes`
(`List<String>`), `status` (`EntityStatus`, STRING), `auditMetadata`.

`AllowanceCatalogueEntity.limitFormula` is read in exactly one place —
`RestructuringEngine` reads `limit.getLimitFormula()` off the **`AllowanceLimitEntity`**, not the
catalogue (`recommendation/RestructuringEngine.java:114,117`). The catalogue's own `limitFormula`
has no reader.

### 2.6 `allowance_limits` — `taxplanning/policy/entity/AllowanceLimitEntity.java:20`

**The only entity in the module carrying indexes.**

| Field | Type | Mongo field | Index | Line |
|---|---|---|---|---|
| `id` | `String` | `_id` | `@Id` | 26-27 |
| `allowanceCode` | `String` | `allowance_code` | `@Indexed` | 29-31 |
| `taxYear` | `String` | `tax_year` | `@Indexed` | 33-35 |
| `financialYear` | `String` | `financial_year` | — | 37-38 |
| `assessmentYear` | `String` | `assessment_year` | — | 40-41 |
| `regimeType` | `RegimeType` | `regime_type` (STRING) | `@Indexed` | 43-45 |
| `employerType` | `EmployerType` | `employer_type` (STRING) | `@Indexed` | 47-49 |
| `annualLimitFixed` | `Long` | `annual_limit_fixed` | — | 51-52 |
| `monthlyLimitFixed` | `Long` | `monthly_limit_fixed` | — | 54-55 |
| `limitFormula` | `String` | `limit_formula` | — | 57-58 |
| `ratePercent` | `Double` | `rate_percent` | — | 60-61 |
| `requiresDocumentation` | `Boolean` | `requires_documentation` | — | 63-64 |
| `requiresEmployerAction` | `Boolean` | `requires_employer_action` | — | 66-67 |
| `status` | `EntityStatus` | `status` (STRING) | — | 69-70 |
| `notifiedDate` | `LocalDate` | `notified_date` | — | 72-73 |
| `effectiveDate` | `LocalDate` | `effective_date` | — | 75-76 |
| `sourceReference` | `String` | `source_reference` | — | 78-79 |
| `auditMetadata` | `AuditMetadata` | `audit_metadata` | — | 81-83 |

All four indexes are **single-field, non-unique**. The queries used are compound
(`policy/repository/AllowanceLimitRepository.java:13-20`), so no declared index matches a query
prefix beyond one field.

`effectiveDate` drives `OrderByEffectiveDateDesc` in all three resolution queries
(`AllowanceLimitRepository.java:13,16,19`) but is **null on every seeded row** —
`allowance-limits-2025-26.json` contains no `effectiveDate` key. `financialYear`,
`assessmentYear`, `notifiedDate`, `requiresDocumentation`, `requiresEmployerAction` have no reader
in the module.

### 2.7 `tax_year_registry` — `taxplanning/policy/entity/TaxYearRegistryEntity.java:17`

**Dead collection.** Three `@Indexed(unique = true)` fields — `taxYear` (`:24-26`),
`financialYear` (`:28-30`), `assessmentYear` (`:32-34`) — plus `startDate`, `endDate`,
`governingAct`, `governingRules`, `defaultRegime`, `status`, `remarks`, `auditMetadata` (`:36-59`).

`grep -rn "TaxYearRegistry"` across `backend/src`, excluding the entity and its repository, returns
**nothing**. `PolicySeederService` does not seed it (`policy/service/PolicySeederService.java:38-45`
seeds slabs, perquisite, catalogue, limits only). It is listed as a seeded reference collection in
`AbstractIntegrationTest.java:47`, so the test harness protects a collection that is always empty.

This is the module's only place where an FY↔AY mapping, a governing act, or a rules-effective-date
window could live. See `03-coverage-matrix.md` §7.

---

## 3. API surface

### 3.1 Authenticated — `TaxPlanningController`, `@RequestMapping("/tax-planning")` (`:34-35`)

| # | Method | Path | Request body | Response | Auth | Line |
|---|---|---|---|---|---|---|
| 1 | POST | `/user/{email}/profile` | `SalaryProfileRequest` | `SalaryProfileResponse` | JWT + path-email match | 48-51 |
| 2 | GET | `/user/{email}/profiles` | — | `List<SalaryProfileResponse>` | JWT + path-email match | 53-56 |
| 3 | GET | `/user/{email}/profile/{profileId}` | — | `SalaryProfileResponse` | JWT + path-email match | 58-61 |
| 4 | PUT | `/user/{email}/profile/{profileId}` | `SalaryProfileRequest` | `SalaryProfileResponse` | JWT + path-email match | 63-66 |
| 5 | DELETE | `/user/{email}/profile/{profileId}` | — | `204 No Content` | JWT + path-email match | 68-72 |
| 6 | POST | `/user/{email}/profile/{profileId}/compute` | — | `TaxComputationEntity` | JWT + path-email match | 76-79 |
| 7 | POST | `/user/{email}/profile/{profileId}/restructure` | — | `RestructuringResult` | JWT + path-email match | 83-87 |
| 8 | GET | `/user/{email}/profile/{profileId}/document/tax-report` | — | `byte[]` PDF | JWT + path-email match | 91-97 |
| 9 | POST | `/admin/policies/slab` | `TaxSlabPolicyEntity` | `TaxSlabPolicyEntity` | `@PreAuthorize("hasRole('SUPER_USER')")` | 101-105 |
| 10 | POST | `/admin/policies/perquisite` | `PerquisitePolicyEntity` | `PerquisitePolicyEntity` | `SUPER_USER` | 107-111 |
| 11 | PUT | `/admin/allowances/{code}?taxYear=` | `AllowanceCatalogueEntity` | `AllowanceCatalogueEntity` | `SUPER_USER` | 113-117 |
| 12 | GET | `/admin/policies?taxYear=` | — | `Map<String,Object>` with keys `taxYear`, `slabPolicies`, `perquisitePolicy`, `allowanceCatalogue` | `SUPER_USER` | 119-128 |

Endpoint 8 **recomputes and re-persists** before rendering: it calls
`taxComputationService.compute(...)` (`:93`), not `getComputation(...)`. A GET that writes.

Endpoint 11 replaces only four fields on the existing document —
`annualLimitFixed`, `monthlyLimitFixed`, `description`, `status`
(`policy/service/PolicyService.java:73-76`). Every other field in the request body is silently
discarded, including `limitFormula`.

### 3.2 Public — `TaxPlanningPublicController`, `@RequestMapping("/tax-planning/public")` (`:16-17`)

| # | Method | Path | Query params | Response | Auth | Line |
|---|---|---|---|---|---|---|
| 13 | GET | `/allowances` | `taxYear` (required), `regime` (optional `RegimeType`) | `List<AllowanceCatalogueEntity>` | **none** — `permitAll` | 24-32 |
| 14 | GET | `/allowances/{code}` | `taxYear` (required), `regime` (optional) | `AllowanceCatalogueEntity` or `404` | **none** | 34-47 |

Endpoint 14 fetches the whole catalogue for the year and filters in memory
(`:39-46`), rather than using `AllowanceCatalogueRepository.findByCodeAndTaxYear`
(`policy/repository/AllowanceCatalogueRepository.java:16`), which exists and is unused.

### 3.3 Authentication and authorization mechanics

`auth/config/AuthConfig.java:37,39`:

```java
.requestMatchers("/auth/login", "/auth/register", "/helper/**", "/finances/**", "/template/**", "/tax-planning/public/**").permitAll()
.requestMatchers(..., "/tax-planning/**", ...).authenticated()
```

Per-user isolation is **not** enforced in the controller or service by comparing the principal; it is
enforced in the filter by string comparison on the URL. `auth/filter/AuthFilter.java:50-52`:

```java
if (UserValidator.isRestrictedUser(username, userAuthorities)) {
    throw new BadCredentialsException("User: " + username + " does not have access");
}
```

`auth/service/UserValidator.java:25-32,42-53`:

```java
public static boolean isRestrictedUser(String username, List<GrantedAuthority> authorities) {
    if(isSuperUser(authorities)) {
        return false;
    }
    HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    String email = extractUsernameFromPath(request);
    return email != null && !username.equals(email);
}

private static String extractUsernameFromPath(String path) {
    if (!path.contains("/user/")) {
        return null;
    } else {
        List<String> argumentList = TStringUtil.splitSafeTrimmed(path, "/");
        return TCollectionUtil.getElementSafe(argumentList, argumentList.indexOf("user") + 1);
    }
}
```

A second layer exists in the services: every profile read re-filters on email, e.g.
`salary/service/SalaryProfileService.java:91-94` and `service/TaxComputationService.java:37-40`:

```java
SalaryProfileEntity profile = profileRepo.findById(profileId)
        .filter(p -> p.getEmail().equals(email))
        .orElseThrow(() -> new IllegalArgumentException(
                "Salary profile not found with id=" + profileId + " for email=" + email));
```

Note that this compares against the `{email}` **path variable**, not the authenticated principal —
so it is a consistency check between path and stored document, and depends on `AuthFilter` having
already matched path to principal.

`SUPER_USER` bypasses the path check entirely (`UserValidator.java:26-28,34-36`) and can read any
user's profile — asserted by `TaxPlanningControllerIntegrationTest.java:430-455`.

### 3.4 Request validation

`SalaryProfileRequest` carries **no Bean Validation annotations** — the file has no
`jakarta.validation` import (`salary/dto/SalaryProfileRequest.java:1-10`), and no controller method
uses `@Valid` (`TaxPlanningController.java:49,64`). All validation is hand-written in
`SalaryProfileService.validateRequest` (`:180-208`):

```java
private void validateRequest(SalaryProfileRequest request) {
    if (request.getMonthlyRentPaid() != null && request.getMonthlyRentPaid() < 0) {
        throw new IllegalArgumentException("monthlyRentPaid must be >= 0");
    }
    if (request.getNumberOfChildren() != null &&
            (request.getNumberOfChildren() < 0 || request.getNumberOfChildren() > 10)) {
        throw new IllegalArgumentException("numberOfChildren must be between 0 and 10");
    }
    if (request.getInvestment80c() != null && request.getInvestment80c() < 0) {
        throw new IllegalArgumentException("investment80c must be >= 0");
    }
    if (request.getInvestment80d() != null && request.getInvestment80d() < 0) {
        throw new IllegalArgumentException("investment80d must be >= 0");
    }
    if (request.getHomeLoanInterest() != null && request.getHomeLoanInterest() < 0) {
        throw new IllegalArgumentException("homeLoanInterest must be >= 0");
    }
    if (request.getNpsSelf80ccd1b() != null && request.getNpsSelf80ccd1b() < 0) {
        throw new IllegalArgumentException("npsSelf80ccd1b must be >= 0");
    }
    if (request.getComponents() != null) {
        for (SalaryComponentDto component : request.getComponents()) {
            if (component.getAnnualAmount() != null && component.getAnnualAmount() < 0) {
                throw new IllegalArgumentException(
                        "annualAmount for component " + component.getAllowanceCode() + " must be >= 0");
            }
        }
    }
}
```

Plus one create-only rule (`:36-42`):

```java
long componentSum = request.getComponents() == null ? 0L
        : request.getComponents().stream()
                .mapToLong(c -> c.getAnnualAmount() != null ? c.getAnnualAmount() : 0L)
                .sum();
if (componentSum <= 0) {
    throw new IllegalArgumentException("Total CTC must be greater than zero");
}
```

`taxYear` is **never validated** against anything — not against `tax_year_registry`, not against a
format, not against the seeded `"2025-26"`. `allowanceCode` on a component is **never validated**
against the catalogue.

---

## 4. Configuration

| Item | Finding | Evidence |
|---|---|---|
| Tax-specific properties | **NOT FOUND.** `grep -rn -i "tax"` over `application.yaml`, `application-staging.yaml`, `application-prod.yaml` returns nothing | `backend/src/main/resources/` contains exactly these three YAML files |
| Feature flags | **NOT FOUND.** No `@ConditionalOnProperty`, no `@Value` in the module | grep over the module |
| Profiles affecting the module | `integration-test` disables `ResponseWrapperAdvice` so integration tests assert unwrapped payloads | per CLAUDE.md; `AbstractIntegrationTest.java:31` `@ActiveProfiles("integration-test")` |
| Seeding trigger | `@PostConstruct` — runs on **every** application start, in every profile, with no flag | `policy/service/PolicySeederService.java:37-45` |
| JaCoCo coverage gate | **None covers `taxplanning`** — the two rules scope to `brokercharges.engine*` and `brokercharges.service.*` | `pom.xml:250,268` |
| PIT mutation target | `com.thiru.wealthlens.taxplanning.engine.*` is a target; `taxplanning.*` is a target-test package | `pom.xml:399,403` |

The seeder is **idempotent by presence check, not by content**. Each of the four `seed*` methods
returns early if any ACTIVE row already exists for `"2025-26"`
(`PolicySeederService.java:57-60, 75-78, 93-96, 120-123`). The tax year is a hardcoded local string
in all four (`:54, 73, 91, 118`). Every failure is caught and logged at ERROR, never rethrown
(`:66-68, 84-86, 111-113, 134-136`) — a seeding failure leaves the application up with missing
policy data, and the first `compute` call then fails with
`"No active tax slab policy found for year=..."` (`policy/service/PolicyService.java:29-30`).

---

## 5. External inputs — how salary data enters

| Route | Status | Evidence |
|---|---|---|
| Manual JSON entry via `POST/PUT /tax-planning/user/{email}/profile` | **The only route.** | `TaxPlanningController.java:48-51, 63-66` |
| Form 16 upload (Part A or Part B) | **NOT FOUND** | No endpoint, no entity, no parser. `grep -rn -i "form16\|form 16\|form-16"` over `backend/src` `*.java` returns **zero hits**; the only matches are 5 occurrences inside `allowance-catalogue-2025-26.json` as free-text strings in `documentsToKeep`/`commonMistakes` arrays (lines 125, 165, 192, 1265, 1300, 1339, 1379) |
| Payslip upload | **NOT FOUND** — `grep -i "payslip\|pay slip"` returns nothing | — |
| PDF parsing | **NOT FOUND** — no PDFBox, no `PDDocument`; the only PDF class in the module is `ITextRenderer`, which writes | `document/DocumentGenerationService.java:14,40` |
| Excel parsing | **NOT FOUND** — Apache POI is in the build (`backend/pom.xml:60`) but has no import under `taxplanning` | — |
| `MultipartFile` intake | **NOT FOUND** — no `MultipartFile` import in the module | grep over the module |
| AI/LLM parsing | Vestigial only. `SalaryProfileEntity.parsedByAi` (`:117-118`) and `sourceText` (`:114-115`) exist with no writer | §2.1 |
| `ParsedCtcDto` | **Orphan.** A 45-field DTO describing a parsed CTC — `employerName`, `totalCtcAnnual`, `basicAnnual`, `hraAnnual`, `ltaAnnual`, `specialAllowanceAnnual`, `mealVoucherAnnual`, `telephoneAnnual`, `fuelAllowanceAnnual`, `driverAllowanceAnnual`, `medicalReimbursementAnnual`, `booksPeriodicalsAnnual`, `uniformAllowanceAnnual`, `childrenEducationAnnual`, `hostelAllowanceAnnual`, `giftVoucherAnnual`, `npsEmployerAnnual`, `pfEmployerAnnual`, `gratuityAnnual`, `performanceBonusAnnual`, `variablePayAnnual`, `otherAllowancesAnnual`, `cityName`, `carProvided`, `carEngineAbove1600cc`, `driverProvided`, `esopPresent`, `esopDetails`, `confidence`, `unmappedComponents`, `parsingNotes`. `grep -rn "ParsedCtcDto"` finds **only its own declaration** | `parsing/ParsedCtcDto.java:1-46` |

**Extraction failure handling: N/A — there is no extraction.**

The fields `confidence`, `unmappedComponents` and `parsingNotes` on `ParsedCtcDto`
(`:43-45`) indicate an intended design where parsing confidence and unmapped components would be
surfaced, but no code consumes them.

---

## 6. Outputs

### 6.1 JSON

Every endpoint returns JSON. Per CLAUDE.md, `shared/advice/ResponseWrapperAdvice` wraps each body in
`ApiResponse<T>` outside the `integration-test` profile.

Two endpoints return **entities directly rather than DTOs**: `computeTax` returns
`TaxComputationEntity` (`TaxPlanningController.java:77-79`) and the admin/public catalogue endpoints
return `AllowanceCatalogueEntity` / `TaxSlabPolicyEntity` / `PerquisitePolicyEntity`
(`:103,109,115,121-127`; `TaxPlanningPublicController.java:25,35`). This exposes `auditMetadata`,
`status` and `_id` to the caller.

### 6.2 PDF — `GET /user/{email}/profile/{profileId}/document/tax-report`

Pipeline: `TaxPlanningController.generateTaxReport` (`:91-97`) →
`TaxComparisonReportGenerator.generate` (`document/TaxComparisonReportGenerator.java:15-17`) →
`DocumentGenerationService.generateTaxComparisonReport` (`document/DocumentGenerationService.java:23-36`).

Template variables bound (`DocumentGenerationService.java:28-33`):

```java
Context context = new Context();
context.setVariable("profile", SalaryProfileResponse.fromEntity(profile));
context.setVariable("computation", computation);
context.setVariable("restructuring", restructuring);
context.setVariable("generatedOn", LocalDate.now());
context.setVariable("disclaimer", "This is an indicative computation only. Consult a qualified CA for advice.");
String html = templateEngine.process("taxplanning/tax-comparison-report", context);
```

The controller always passes `null` for `restructuring` (`TaxPlanningController.java:95`), and the
template guards on it (`tax-comparison-report.html:119` — `th:if="${restructuring != null}"`), so
**the restructuring and recommendations sections of the PDF are unreachable through the API**.

Template: `backend/src/main/resources/templates/taxplanning/tax-comparison-report.html`, 179 lines.

Contents, in order:

| Section | Rows / fields | Template lines |
|---|---|---|
| Header | title, `generatedOn` formatted `dd MMMM yyyy` | 11-13 |
| Employee details | `profileName`, `email`, `employerName`, `taxYear`, `cityName`, `regimeType`, `annualCtc` | 21-33 |
| Side-by-side comparison table (new vs old) | `grossSalary`, `totalExemptions`, `totalDeductions`, `taxableIncome`, `basicTaxBeforeRebate`, `rebate87aApplied`, `taxAfterRebate`, `surcharge`, `cess`, `totalTax`, `netTakeHome` | 53-104 |
| Recommendation banner | `recommendedRegime`, `annualSavingVsCurrent`, a star glyph per regime | 112-114 |
| Restructuring comparison *(unreachable — `restructuring` is always null)* | `restructuredNewRegimeTax`, `restructuredOldRegimeTax` | 119-133 |
| Recommendations table *(unreachable)* | per row: `priority`, `displayName`, `suggestedAnnualAmount`, `estimatedTaxSaving`, `actionRequired` | 150-155 |
| Total optimized saving *(unreachable)* | `totalOptimizedSaving` | 162 |
| Disclaimer | `disclaimer` | 175 |

All money is rendered with `#numbers.formatDecimal(..., 1, 'COMMA', 0, 'POINT')` — grouped
thousands, zero decimal places.

`convertHtmlToPdf` wraps every failure in a bare `RuntimeException`
(`DocumentGenerationService.java:45-47`), which `ControllerAdviser` maps to 500.

### 6.3 Excel

**NOT FOUND.** No Excel export exists in this module.

---

## 7. Events, schedulers, async jobs

**NOT FOUND.** `grep` over the module for `@Scheduled`, `@Async`, `@EventListener`,
`ApplicationModuleListener`, `@TransactionalEventListener` returns nothing.

The single lifecycle hook is `PolicySeederService.seed()` at `@PostConstruct`
(`policy/service/PolicySeederService.java:37-45`) — synchronous, on the startup thread.

---

## 8. Transaction boundaries

| Class | Annotation | Line |
|---|---|---|
| `SalaryProfileService` | `@Transactional` (class level, read-write) | `salary/service/SalaryProfileService.java:25` |
| `TaxComputationService` | `@Transactional(readOnly = true)` class level; `compute` overridden to read-write | `service/TaxComputationService.java:25,34` |
| `PolicyService` | `@Transactional(readOnly = true)` class level; three write methods overridden | `policy/service/PolicyService.java:20,47,58,68` |
| `RestructuringEngine` | **none** | `recommendation/RestructuringEngine.java:30-33` |
| `AllowanceResolutionService` | **none** | `policy/service/AllowanceResolutionService.java:18-21` |
| `PolicySeederService` | **none** | `policy/service/PolicySeederService.java:25-28` |

Per CLAUDE.md, multi-document `@Transactional` writes require a replica set **and**
`app.mongodb.transactions-enabled=true`.
