# Module `taxplanning` — profiles, drafts, computations, documents and tax operations

> Written from the code at `review/taxplanning`; every claim cites the line it was read from, and a bare path is under
> `backend/src/main/java/com/thiru/wealthlens/taxplanning/`. Template: `docs/taxplanning-build/plans/W-357b.md` C2a. The module is
> written in four parts (D-923): boundary, profiles, workspaces, drafts and salary import (W-357b10a, D-924); assembly, portfolio
> bridge, computation, advisory views and report (W-357b10b, D-925); documents (**W-357b10c**, to come); protection, erasure,
> jobs and the Gazette watch (**W-357b10d**, to come).

## 1. Purpose

`taxplanning` is the application layer for Indian income-tax planning (`package-info.java:1` – `:7`). It owns persistence,
access checks, and HTTP endpoints for:
- **Taxpayer profiles and reference records** (`profile/`, `enums/`): individuals and HUFs, their family members, employers,
  landlords, lenders and bank accounts.
- **Tax-year workspaces, versioned filing drafts and salary workbook import** (`workspace/`, `workspace/importing/`): per-year
  drafts carrying the engine's own input model, append-only revisions with optimistic concurrency, draft status transitions,
  regime-election history, and isolated `.xlsx` salary-structure import.
- **Assembly, portfolio bridging, computations, advisory views and PDF reports** (`assemble/`, `portfolio/`, `compute/` —
  W-357b10b).
- **Uploaded certificates, child-process isolation, parsing and reconciliation** (`documents/`, `isolation/` — W-357b10c).
- **Access logging, profile/account erasure, scheduled/on-demand jobs and Gazette watch** (`protection/`, `erasure/`, `jobs/`,
  `gazette/`, `controller/` — W-357b10d).

All tax calculations are delegated to `taxengine`, which is pure and fetches nothing (`package-info.java:5` – `:6`).

## 2. Boundaries

- **`CLOSED` module,** `allowedDependencies = {"shared", "auth", "taxengine :: *", "portfolio"}` (`package-info.java:8` – `:11`).
- **No `@NamedInterface`:** no other module imports `com.thiru.wealthlens.taxplanning` (grep: 0 external importers).
- **URL prefix:** `/api/v1/{zone}/tax` — `taxplanning` maps to module segment `tax` (`shared/web/ApiZones.java:26` – `:27`, `:33` –
  `:36`).
- **Part one's cross-module uses:**
  - `shared`: `@MeApi`, `@CallerEmail`, `BadRequestException`, `AuditableEntity`, `CreatedWithId`, `EncryptedField`,
    `SearchableIdentifier`, `IdentifierDigest`, `TCollectionUtil.DATE_FORMAT`.
  - `taxengine`: `TaxCalculator` (`declarations`, `requireAsked`, `payLineComponents`), input types (`EnteredFacts`,
    `FilingFacts`, `LedgerSnapshot`, `Declaration`, `FairMarketValueBasis`, `FamilySnapshot.Relationship`, `Tenancy`, `Journey`,
    `QualifyingPayment`), `ResidentialStatus`, `DisabilitySeverity`, `TaxYearId`, `ComponentCode`, `DeclarationCode`, `Money`,
    `DeclarationQuestion`, `PayLineComponent`.

- **Part two's cross-module uses:**
  - `portfolio`: `CapitalGainsSummaryService` and its read-model (`CapitalGainsSummary`, `CapitalGainsBucket`, `CapitalGainsAmounts`,
    `SideCharges`, `TradeVenue`; `portfolio/CapitalGainsAdapter.java:3` – `:8`); `PortfolioRepository` and `AssetEntity`
    (`compute/HeldAssetsViewService.java:3` – `:4`).
  - `taxengine`: `TaxCalculator` (`compute`, `billWithRoomsFilled`, `compareRegimes`, `chooseForm`, `optimise`, `defaultRegime`,
    `packVersions`, `ruleChecksums`, `verificationLevels`, `instrumentCitations`, `computationSentences`, `reportSentences`,
    `evidenceSentences`, `claimSentences`, `priceClaims`, `claimPlaybooks`, `priceDeductionRoom`, `priceRentRoom`, `regimeElection`,
    `termIfSold`, `certificate`, `salaryCertificateLaw`), `EngineInputs`, `CapitalGainsInput`, `Assessee` (`Individual`, `Huf`),
    `FamilySnapshot`, `RegimeElectionSnapshot`, `ElectionState`, `TemporalContext`, `AllocationSpace`, `ProvidentFundTerms`,
    `EmployerFund`, `ComputedTax`, `ItrFormChoice`, `Optimisation`, `RegimeComparison`, `ClaimPlaybook`, `PricedClaim`, `RoomFamily`.
  - `shared`: `ExcelWorkbooks` / `ExcelSheet` / `ExcelColumn` (`compute/HeldAssetsWorkbook.java:3` – `:5`), `FinancialYear`
    (`portfolio/CapitalGainsAdapter.java:9`), `TLocalDateTime.COMPLETE_DATE_TIME_FORMAT` (`compute/ComputationListing.java:4`).

## 3. Routes

Every route in parts one and two is in the `me` zone (`@MeApi`, prefix `/api/v1/me/tax`), taking the authenticated user from `@CallerEmail`.

### Profiles and reference records (`profile/controller/`)

| Method and path | Body → answer | Where |
|---|---|---|
| `POST /api/v1/me/tax/profiles` | `@Valid TaxpayerProfileRequest` → `TaxpayerProfileResponse` (PAN masked) | `profile/controller/TaxProfileController.java:21` – `:31` |
| `GET /api/v1/me/tax/profiles` | → `List<TaxpayerProfileResponse>` (active profiles only; logs `PROFILE` read per profile) | `profile/controller/TaxProfileController.java:33` – `:36`; `profile/TaxProfileService.java:36` – `:41` |
| `GET /api/v1/me/tax/profiles/{profileId}` | → `TaxpayerProfileResponse` (logs `PROFILE` read) | `profile/controller/TaxProfileController.java:38` – `:41`; `profile/TaxProfileService.java:44` – `:48` |
| `PUT /api/v1/me/tax/profiles/{profileId}` | `@Valid TaxpayerProfileRequest` → `TaxpayerProfileResponse` | `profile/controller/TaxProfileController.java:43` – `:47` |
| `POST /api/v1/me/tax/profiles/{profileId}/family-members` | `@Valid FamilyMemberRequest` → `FamilyMemberResponse` (PAN masked) | `profile/controller/TaxProfileReferenceController.java:29` – `:39` |
| `GET /api/v1/me/tax/profiles/{profileId}/family-members` | → `List<FamilyMemberResponse>` (logs `FAMILY_MEMBERS` read) | `profile/controller/TaxProfileReferenceController.java:41` – `:44`; `profile/TaxProfileReferenceService.java:47` – `:51` |
| `PUT /api/v1/me/tax/profiles/{profileId}/family-members/{id}` | `@Valid FamilyMemberRequest` → `FamilyMemberResponse` | `profile/controller/TaxProfileReferenceController.java:46` – `:50` |
| `POST /api/v1/me/tax/profiles/{profileId}/employers` | `@Valid EmployerRequest` → `EmployerResponse` (TAN and PAN masked) | `profile/controller/TaxProfileReferenceController.java:52` – `:55` |
| `GET /api/v1/me/tax/profiles/{profileId}/employers` | → `List<EmployerResponse>` | `profile/controller/TaxProfileReferenceController.java:57` – `:60` |
| `PUT /api/v1/me/tax/profiles/{profileId}/employers/{id}` | `@Valid EmployerRequest` → `EmployerResponse` | `profile/controller/TaxProfileReferenceController.java:62` – `:66` |
| `POST /api/v1/me/tax/profiles/{profileId}/landlords` | `@Valid LandlordRequest` → `LandlordResponse` (PAN masked) | `profile/controller/TaxProfileReferenceController.java:68` – `:71` |
| `GET /api/v1/me/tax/profiles/{profileId}/landlords` | → `List<LandlordResponse>` | `profile/controller/TaxProfileReferenceController.java:73` – `:76` |
| `PUT /api/v1/me/tax/profiles/{profileId}/landlords/{id}` | `@Valid LandlordRequest` → `LandlordResponse` | `profile/controller/TaxProfileReferenceController.java:78` – `:82` |
| `POST /api/v1/me/tax/profiles/{profileId}/lenders` | `@Valid LenderRequest` → `LenderResponse` (PAN masked) | `profile/controller/TaxProfileReferenceController.java:84` – `:87` |
| `GET /api/v1/me/tax/profiles/{profileId}/lenders` | → `List<LenderResponse>` | `profile/controller/TaxProfileReferenceController.java:89` – `:92` |
| `PUT /api/v1/me/tax/profiles/{profileId}/lenders/{id}` | `@Valid LenderRequest` → `LenderResponse` | `profile/controller/TaxProfileReferenceController.java:94` – `:98` |
| `POST /api/v1/me/tax/profiles/{profileId}/bank-accounts` | `@Valid BankAccountRequest` → `BankAccountResponse` | `profile/controller/TaxProfileReferenceController.java:100` – `:103` |
| `GET /api/v1/me/tax/profiles/{profileId}/bank-accounts` | → `List<BankAccountResponse>` | `profile/controller/TaxProfileReferenceController.java:105` – `:108` |
| `PUT /api/v1/me/tax/profiles/{profileId}/bank-accounts/{id}` | `@Valid BankAccountRequest` → `BankAccountResponse` | `profile/controller/TaxProfileReferenceController.java:110` – `:114` |

### Workspaces, drafts and salary import (`workspace/controller/`)

| Method and path | Body / headers → answer | Where |
|---|---|---|
| `GET /api/v1/me/tax/profiles/{profileId}/workspaces/{taxYear}` | → `WorkspaceResponse` (creates the workspace on first asking) | `workspace/controller/DraftController.java:45` – `:48`; `workspace/DraftService.java:52` – `:65` |
| `GET /api/v1/me/tax/profiles/{profileId}/workspaces/{taxYear}/drafts` | → `List<DraftView>` (logs `DRAFT` read per draft) | `workspace/controller/DraftController.java:50` – `:53`; `workspace/DraftService.java:76` – `:80` |
| `POST /api/v1/me/tax/profiles/{profileId}/workspaces/{taxYear}/drafts` | `Map<String, String>` (`label`) → `DraftView` + `ETag: "rev-0"` | `workspace/controller/DraftController.java:55` – `:59`, `:107` – `:113` |
| `GET /api/v1/me/tax/declarations/{taxYear}` | → `List<DeclarationQuestion>` (empty when no pack ships for the year; no access log) | `workspace/controller/DraftController.java:62` – `:65`; `workspace/DraftService.java:71` – `:73`; `taxengine/api/TaxCalculator.java:606` – `:615` |
| `GET /api/v1/me/tax/drafts/{draftId}` | → `DraftView` + `ETag: "rev-N"` (logs `DRAFT` read) | `workspace/controller/DraftController.java:67` – `:70`; `workspace/DraftService.java:141` – `:145` |
| `PATCH /api/v1/me/tax/drafts/{draftId}` | `If-Match: "rev-N"` + `JsonNode` (RFC 6902 patch array) → `DraftView` + new `ETag`, or **409** `DraftConflict` + current `ETag` | `workspace/controller/DraftController.java:72` – `:80`, `:103` – `:121` |
| `POST /api/v1/me/tax/drafts/{draftId}/status` | `@Valid DraftStatusRequest` (`status`, optional `regimeCode`) → `DraftView` + `ETag` | `workspace/controller/DraftController.java:82` – `:86` |
| `POST /api/v1/me/tax/drafts/{draftId}/primary` | → `WorkspaceResponse` | `workspace/controller/DraftController.java:88` – `:91` |
| `GET /api/v1/me/tax/drafts/{draftId}/revisions` | `Pageable` → `PagedModel<RevisionResponse>` (oldest first: `revisionNumber`, then `id`) | `workspace/controller/DraftController.java:93` – `:96`; `workspace/DraftService.java:219` – `:223` |
| `GET /api/v1/me/tax/drafts/{draftId}/revisions/{number}` | → `JsonNode` (inputs replayed from revision 0; logs `DRAFT_REVISION` read) | `workspace/controller/DraftController.java:98` – `:101`; `workspace/DraftService.java:226` – `:240` |
| `POST /api/v1/me/tax/drafts/{draftId}/salary-import` | `multipart/form-data` part `file` (`.xlsx`) → `SalaryImportPreview` (stores nothing) | `workspace/controller/SalaryImportController.java:32` – `:36` |
| `POST /api/v1/me/tax/drafts/{draftId}/salary-import/apply` | `If-Match: "rev-N"` + `multipart/form-data` parts `file` and `decisions` (`SalaryImportDecisions`) → `DraftView` + new `ETag`, or **409** `DraftConflict` + current `ETag` | `workspace/controller/SalaryImportController.java:38` – `:47` |

### Computations, advisory views, reports and held assets (`compute/controller/`)

| Method and path | Body / params → answer | Where |
|---|---|---|
| `POST /api/v1/me/tax/drafts/{draftId}/compute` | `Map<String, String>` (`regime`) → `DraftComputationView` (stored in `tax_draft_computations`, clears draft staleness) | `compute/controller/DraftComputationController.java:70` – `:74`; `compute/DraftComputationService.java:84` – `:109` |
| `POST /api/v1/me/tax/profiles/{profileId}/workspaces/{taxYear}/first-figure` | `@Valid DraftFirstFigureRequest` (`label`, `grossPay`, `dueDate`) → `DraftFirstFigure` (creates draft, patches step 1, computes and stores every compared regime) | `compute/controller/DraftComputationController.java:76` – `:80`; `compute/DraftFirstFigureService.java:41` – `:52` |
| `POST /api/v1/me/tax/drafts/{draftId}/compare-regimes` | → `DraftRegimeComparison` (read-only, nothing stored) | `compute/controller/DraftComputationController.java:82` – `:85`; `compute/DraftComputationService.java:138` – `:146` |
| `GET /api/v1/me/tax/drafts/{draftId}/forecast` | → `DraftForecast` (read-only, nothing stored) | `compute/controller/DraftComputationController.java:87` – `:90`; `compute/DraftForecastService.java:43` – `:52` |
| `GET /api/v1/me/tax/drafts/{draftId}/reconciliation` | → `DraftReconciliation` (behind `app.tax.documents.enabled`; logs `DOCUMENT` read per confirmed certificate) | `compute/controller/DraftComputationController.java:92` – `:95`; `compute/DraftReconciliationService.java:65` – `:88` |
| `GET /api/v1/me/tax/drafts/{draftId}/calendar` | → `DraftCalendar` (read-only, nothing stored) | `compute/controller/DraftComputationController.java:97` – `:100`; `compute/DraftCalendarService.java:48` – `:58` |
| `GET /api/v1/me/tax/drafts/{draftId}/completeness` | → `DraftCompleteness` (top 5 surfaced + rest; read-only) | `compute/controller/DraftComputationController.java:102` – `:105`; `compute/DraftCompletenessService.java:48` – `:59` |
| `GET /api/v1/me/tax/drafts/{draftId}/onboarding` | → `DraftOnboarding` (5 steps in order + `next`; read-only) | `compute/controller/DraftComputationController.java:107` – `:110`; `compute/DraftOnboardingService.java:49` – `:57` |
| `GET /api/v1/me/tax/drafts/{draftId}/claims` | → `DraftClaims` (`claimed` priced + `notClaimed`; read-only) | `compute/controller/DraftComputationController.java:112` – `:115`; `compute/DraftClaimsService.java:50` – `:60` |
| `GET /api/v1/me/tax/drafts/{draftId}/claim-integrity` | → `DraftClaimIntegrity` (per-rule allowed vs evidenced; read-only) | `compute/controller/DraftComputationController.java:117` – `:120`; `compute/DraftClaimIntegrityService.java:58` – `:67` |
| `GET /api/v1/me/tax/drafts/{draftId}/document-checklist` | → `DraftDocumentChecklist` (per claimed playbook's required documents; read-only) | `compute/controller/DraftComputationController.java:122` – `:125`; `compute/DraftDocumentChecklistService.java:57` – `:69` |
| `POST /api/v1/me/tax/drafts/{draftId}/itr-form` | `Map<String, String>` (`regime`, `returnKind`) → `ItrFormChoice` (read-only) | `compute/controller/DraftComputationController.java:127` – `:131`; `compute/DraftComputationService.java:156` – `:167` |
| `POST /api/v1/me/tax/drafts/{draftId}/optimise` | `@Valid DraftOptimisationRequest` → `DraftOptimisation` (engine `Optimisation` + `hrSupport`; read-only) | `compute/controller/DraftComputationController.java:133` – `:137`; `compute/DraftComputationService.java:177` – `:186` |
| `GET /api/v1/me/tax/profiles/{profileId}/capital-gains/{taxYear}` | `?asOf=yyyy-MM-dd` (optional) → `CapitalGainsView` (portfolio holder only; read-only) | `compute/controller/DraftComputationController.java:139` – `:143`; `compute/CapitalGainsViewService.java:35` – `:49` |
| `GET /api/v1/me/tax/computations/{receipt}` | → `DraftComputationView` (logs `COMPUTATION` read) | `compute/controller/DraftComputationController.java:145` – `:148`; `compute/DraftComputationService.java:220` – `:226` |
| `GET /api/v1/me/tax/profiles/{profileId}/computations` | `Pageable` → `PagedModel<ComputationListing>` (newest `computedAt` first, tie by `receipt`; logs `COMPUTATION` read per listed row) | `compute/controller/DraftComputationController.java:151` – `:154`; `compute/DraftComputationService.java:199` – `:213` |
| `GET /api/v1/me/tax/computations/{receipt}/report` | → `application/pdf` (`attachment; filename="<receipt>.pdf"`, generated on request, kept nowhere) | `compute/controller/DraftComputationController.java:157` – `:162`; `compute/ComputationReportService.java:51` – `:58` |
| `GET /api/v1/me/tax/profiles/{profileId}/held-assets` | `?asOf=yyyy-MM-dd` (optional, default today), `?term=SHORT_TERM\|LONG_TERM` (optional) → `HeldAssetsView` | `compute/controller/HeldAssetsController.java:35` – `:40`; `compute/HeldAssetsViewService.java:46` – `:57` |
| `GET /api/v1/me/tax/profiles/{profileId}/held-assets/excel` | same params → `.xlsx` (`attachment; filename=held-assets-<asOf>.xlsx`, sheets `LOTS` and `TOTALS`) | `compute/controller/HeldAssetsController.java:42` – `:50`; `compute/HeldAssetsWorkbook.java:15` – `:42` |

### Refusals (parts one and two)

Every `BadRequestException` and `IllegalArgumentException` maps to HTTP **400** (`ProblemDetail`, `shared/exception/ControllerAdviser`). `DraftConflictException` is caught by `DraftController` / `SalaryImportController` and answered as HTTP **409** with a `DraftConflict(message, current, conflictingPaths)` body and the current `ETag` (`workspace/controller/DraftController.java:77` – `:79`, `:103` – `:105`; `workspace/controller/SalaryImportController.java:44` – `:46`).

| When | Status & message | Where |
|---|---|---|
| Profile absent, belongs to another account, or held for erasure | 400 `"This account holds no such profile"` | `profile/TaxProfileService.java:56` – `:61` |
| Second profile of the same account sets `holdsPortfolio = true` | 400 `"Another profile of this account already holds its portfolio"` | `profile/TaxProfileService.java:85` – `:88` |
| Duplicate PAN (`pan_digest` unique index) | 400 `"A profile already holds the PAN <masked>"` | `profile/TaxProfileService.java:94` – `:96` |
| Missing/blank name or bank name | 400 `"A name is required"` | `profile/ProfileFacts.java:11` – `:15` |
| Malformed or null PAN (required on profile; validated when non-null on family/employer/landlord/lender) | 400 `"A PAN is five capital letters, four digits and a capital letter"` | `profile/Pan.java:11` – `:17`; `profile/ProfileFacts.java:17` – `:21` |
| Missing/blank employer TAN | 400 `"An employer needs its TAN"` | `profile/Tan.java:9` – `:13` |
| Missing required enum/date on profile or reference requests | 400 `"A profile needs a kind"` / `"A profile needs a residential status"` / `"An individual needs a date of birth"` / `"A family member needs a relationship"` / `"A family member needs a date of birth"` / `"An employer needs a type"` / `"A lender needs a kind"` / `"A bank account needs a kind"` | `profile/TaxpayerProfileRequest.java:14` – `:18`; `FamilyMemberRequest.java:16` – `:17`; `EmployerRequest.java:13`; `LenderRequest.java:10`; `BankAccountRequest.java:9` |
| Reference record absent or belongs to another profile | 400 `"This profile holds no such record"` | `profile/TaxProfileReferenceService.java:152` – `:154` |
| Duplicate TAN on the same profile (or any duplicate key on a reference save) | 400 `"This profile already holds an employer with that TAN"` | `profile/TaxProfileReferenceService.java:161` – `:163` |
| Draft absent (or its profile not owned / held for erasure) | 400 `"This account holds no such draft"` (or profile message) | `workspace/DraftService.java:313` – `:318` |
| Missing/blank draft label | 400 `"A draft needs a label"` | `workspace/DraftService.java:102` – `:104` |
| Missing or malformed `If-Match` header (must match `^"rev-(\d{1,9})"$`) | 400 `"A save needs an If-Match header holding the revision it was made against"` | `workspace/controller/DraftController.java:40`, `:115` – `:121` |
| Patch not a JSON array, unsupported op (not `add`/`remove`/`replace`), missing `value`, empty path, missing parent/target, bad array index, or path not starting with `/` | 400 `"A patch is an array of operations"` / `"A patch may add, remove or replace, but it asked to …"` / `"A patch's <op> needs a value"` / `"A patch needs a path below the document's root"` / `"A patch's target <k> does not exist"` / `"A patch's path passes through a value at <k>"` / `"A patch's path does not exist at <k>"` / `"A patch's array position is out of range at <k>"` / `"A patch's path starts with /"` | `workspace/JsonPatchApplier.java:21` – `:100` |
| Patched inputs fail strict binding to `DraftInputs` | 400 `"The draft's inputs do not fit at /<path>"` (never echoes values) | `workspace/DraftInputsMapper.java:88` – `:97` |
| Newly added landlord/lender id not held by this profile | 400 `"landlord <id> is not one of this profile's landlords"` / `"lender <id> is not one of this profile's lenders"` | `profile/TaxProfileReferenceService.java:110` – `:123`; `workspace/DraftService.java:200` – `:210` |
| Evidence does not match entries by position, exceeds tenancy months / incurred amount / paid total, or names an unentered code / absent Agniveer fund | 400 naming the `/evidence/...` path and bound | `workspace/EvidenceHeld.java:48` – `:91`; `workspace/ChapterViiiEvidence.java:46` – `:70` |
| `/filing/ofWhichByEmployer` negative or exceeds `/filing/taxAtSource` | 400 (`IllegalArgumentException` inside `DraftInputs.Filing`; caught as bind refusal on patch) | `workspace/DraftInputs.java:92` – `:96` |
| `/filing/declarations` names a question not asked for the year or wrong answer kind | 400 (from `TaxCalculator.requireAsked`) | `workspace/DraftService.java:186` – `:187` |
| Illegal draft status transition or null status | 400 `"A draft that is <current> cannot become <next>"` | `workspace/DraftService.java:245` – `:247` |
| Requested revision number does not exist (negative or above `currentRevision`) | 400 `"This draft has no revision <number>"` | `workspace/DraftService.java:235` – `:237` |
| Patch/import against non-editable draft (`ACTED_ON` or `ARCHIVED`) | **409** `"A draft that is <status> cannot be edited"` (`conflictingPaths = []`) | `workspace/DraftService.java:172` – `:175` |
| Patch/import against stale `If-Match` revision | **409** `"This draft has moved on to revision <N>"` + overlapping paths | `workspace/DraftService.java:176` – `:178`, `:290` – `:300` |
| Concurrent save collided on all 5 retries | **409** `"Another save of this draft had not finished; read the draft and send again"` (`conflictingPaths = []`) | `workspace/DraftSaveRetry.java:65` – `:67`; `workspace/DraftService.java:285` – `:288` |
| Salary workbook empty, > 1,048,576 bytes, child process failure, unreadable workbook (no sheet, empty first sheet, missing `Component` or both amount headers, > 200 rows), or invalid rupee cell | 400 `"The workbook is empty"` / `"A salary workbook is at most 1048576 bytes"` / `"The workbook could not be read: …"` / child's `reason` / `"Row <n>: the <annual\|monthly> amount is not a rupee figure"` | `workspace/importing/SalaryImportService.java:66` – `:71`, `:175` – `:192`, `:206` – `:212`; `SalaryStructureWorkbook.java:50` – `:67`, `:84` – `:86` |
| Salary import apply: missing `employment`, row decided twice / stray / undecided, unknown component code, mapped row has no amount, duplicate component across rows, every row left out, `employment` already has `components`, or workbook SHA-256 already imported into this draft | 400 naming the offending rows/code/earlier revision | `workspace/importing/SalaryImportDecisions.java:19` – `:21`; `SalaryImportService.java:119` – `:132`, `:146` – `:169`; `workspace/DraftService.java:164` – `:166` |
| Compute with missing/blank `regime` | 400 `"A computation needs a regime"` | `compute/DraftComputationService.java:85` – `:87` |
| Compute / view on a draft with no `/filing/dueDate` | 400 `"A computation needs the draft's /filing/dueDate"` | `workspace/DraftInputs.java:106` – `:108`; `assemble/EngineInputsAssembler.java:66` |
| First figure missing label, non-positive `grossPay`, or missing `dueDate` | 400 `"A first figure needs /label, the draft's label"` / `"… /grossPay, the year's gross pay, above nil"` / `"… /dueDate, the day the return is due"` | `compute/DraftFirstFigureService.java:54` – `:64` |
| Choose ITR form missing `regime` or `returnKind` | 400 `"A form choice needs a regime, since total income depends on it"` / `"A form choice needs a returnKind, …"` | `compute/DraftComputationService.java:157` – `:163` |
| Optimise missing `movable`, `residual` or `cashFloor`; unknown fund in `movableFunds`; non-numeric `cashFloor`; `verificationBreadth` above `app.tax.optimiser.exhaustive-breadth` | 400 `"An optimisation needs movable, residual and cashFloor"` / `"movableFunds names no fund …"` / `"cashFloor must be an amount in rupees …"` / `"The exhaustive check can cover at most <N> components here, not <asked>"` | `compute/DraftOptimisationRequest.java:35` – `:37`, `:71` – `:75`, `:86` – `:92`; `compute/OptimiserSettings.java:29` – `:36` |
| Capital-gains or held-assets view on a profile with `holdsPortfolio = false` | 400 `"This profile does not hold the account's portfolio"` | `compute/CapitalGainsViewService.java:37` – `:39`; `compute/HeldAssetsViewService.java:48` – `:50` |
| Reconciliation when `app.tax.documents.enabled=false`, or when no comparable certificate for the draft's year is confirmed | **503** (from `DocumentSwitch.requireEnabled`); 400 `"No certificate of this draft's tax year is confirmed yet: confirm one to reconcile it"` | `compute/DraftReconciliationService.java:66`, `:76` – `:78` |
| Receipt absent, belongs to another account, or its draft/profile is held for erasure | 400 `"This account holds no such receipt"` (or draft/profile refusal) | `compute/DraftComputationService.java:220` – `:224` |
| Portfolio amount is NaN, infinite, or has > 2 decimal places (`MoneyBridge.fromTMoney`) | 400 `"A portfolio amount was …"` / `"A portfolio amount of … is not canonical to paise …"` | `portfolio/MoneyBridge.java:26` – `:37` |

## 4. Stored data

Parts one and two cover 12 `@Document` collections: `profile/` (7), `workspace/` (4), and `compute/` (1). Every entity implements `AuditableEntity` (`audit_metadata`), and 11 of the 12 implement `CreatedWithId` (`newlyCreated` transient flag so Spring Data Mongo treats a caller-assigned UUID as an insert; `ProfileErasure` does not).

| Collection | Fields (encrypted: **E** = `EncryptedField`, **S** = `SearchableIdentifier`) | Indexes | Where |
|---|---|---|---|
| `tax_profiles` | `_id` (UUID), `email` (**S**), `pan` (**E**), `pan_digest`, `name` (**E**), `kind` (`INDIVIDUAL`/`HUF`), `date_of_birth` (**E**), `residential_status`, `holds_portfolio`, `disability` (**E**), `date_of_death`, `erasure_requested_at` (UTC), `audit_metadata` | `email`; `pan_digest` (unique, sparse) | `profile/TaxpayerProfile.java:32` – `:101` |
| `tax_family_members` | `_id`, `profile_id`, `name` (**E**), `relationship`, `date_of_birth` (**E**), `dependant`, `disability` (**E**), `date_of_death`, `multiple_birth_after_first_child`, `resident_in_india`, `pan` (**E**), `audit_metadata` | `profile_id` | `profile/FamilyMember.java:29` – `:84` |
| `tax_employers` | `_id`, `profile_id`, `name`, `tan` (**E**), `tan_digest`, `pan` (**E**), `type` (`GOVT`/`PSU`/`PRIVATE`), `address`, `audit_metadata` | `tax_employer_tan_idx`: `{profile_id: 1, tan_digest: 1}` (unique) | `profile/Employer.java:24` – `:67` |
| `tax_landlords` | `_id`, `profile_id`, `name` (**E**), `address` (**E**), `pan` (**E**), `relationship_to_taxpayer`, `audit_metadata` | `profile_id` | `profile/Landlord.java:24` – `:61` |
| `tax_lenders` | `_id`, `profile_id`, `name` (**E**), `address` (**E**), `pan` (**E**), `kind` (`BANK`/`HOUSING_FINANCE_COMPANY`/`EMPLOYER`/`OTHER`), `audit_metadata` | `profile_id` | `profile/Lender.java:23` – `:61` |
| `tax_bank_accounts` | `_id`, `profile_id`, `bank_name`, `ifsc`, `masked_number`, `kind` (`SAVINGS`/`TIME_DEPOSIT`/`CURRENT`/`POST_OFFICE`), `nominated_for_refund`, `audit_metadata` | `profile_id` | `profile/BankAccount.java:21` – `:58` |
| `tax_profile_erasures` | `_id`, `email` (**S**), `profile_id`, `requested_at` (UTC), `erased_at` (UTC), `audit_metadata` | none declared (gap 8) | `profile/ProfileErasure.java:22` – `:45` |
| `tax_year_workspaces` | `_id`, `profile_id`, `tax_year` (`int`), `primary_draft_id`, `audit_metadata` | `tax_workspace_year_idx`: `{profile_id: 1, tax_year: 1}` (unique) | `workspace/TaxYearWorkspace.java:18` – `:46` |
| `tax_filing_drafts` | `_id`, `workspace_id`, `profile_id`, `tax_year`, `label`, `status`, `current_revision`, `inputs_json` (**E**), `regime_election`, `acted_on_at` (UTC), `archived_at` (UTC), `staleness` (`DraftStaleness`), `audit_metadata` | `tax_draft_workspace_idx`: `{workspace_id: 1, status: 1}` | `workspace/FilingDraft.java:25` – `:90`; `DraftStaleness.java:16` – `:29` |
| `tax_draft_revisions` | `_id`, `draft_id`, `revision_number`, `full_state_json` (**E**), `change_set_json` (**E**), `changed_by` (**S**), `reopened`, `imported_from` (`WorkbookImport`: `workbook_sha256`, `rows_applied`, `rows_mapped`, `rows_left_out`), `audit_metadata` | `tax_revision_number_idx`: `{draft_id: 1, revision_number: 1}` (unique) | `workspace/DraftRevision.java:24` – `:69`; `WorkbookImport.java:17` – `:34` |
| `tax_regime_election_history` | `_id`, `profile_id`, `regime_code`, `events` (`List<ElectionEvent(taxYearStart, sourceDraft)>`), `audit_metadata` | `tax_election_regime_idx`: `{profile_id: 1, regime_code: 1}` (unique) | `workspace/RegimeElectionHistory.java:24` – `:73` |
| `tax_draft_computations` | `_id`, `receipt`, `email` (**S**), `draft_id`, `revision`, `result_json` (**E**), `law_and_exec`, `rule_checksums`, `computed_at` (UTC), `audit_metadata` | `receipt` (unique); `draft_id` | `compute/DraftComputation.java:27` – `:79` |

`TaxProfileIndexInitializer` (`profile/TaxProfileIndexInitializer.java:20` – `:35`) creates the indexes for the first six profile collections at `@PostConstruct`, `WorkspaceIndexInitializer` (`workspace/WorkspaceIndexInitializer.java:16` – `:31`) creates the indexes for all four workspace collections, and `DraftComputationIndexInitializer` (`compute/DraftComputationIndexInitializer.java:16` – `:30`) creates the indexes for `tax_draft_computations`; none catches errors, so a failure stops startup.

## 5. Behaviour

### Profiles and reference records (`profile/`)

- **Ownership and erasure hold (`profile/TaxProfileService.java:56` – `:82`):** `owned(email, profileId)` verifies that the profile exists, matches `email`, and has `erasureRequestedAt == null`, throwing the same `BadRequestException` in all three failure cases. `holdForErasure` stamps `erasureRequestedAt` and nulls `panDigest`, freeing the sparse unique index so the PAN can be registered again while the encrypted PAN stays until erasure (`:71` – `:78`).
- **One portfolio holder per account (`profile/TaxProfileService.java:85` – `:88`):** creating or replacing a profile with `holdsPortfolio = true` refuses if another active profile of the same account already has `holdsPortfolio = true`.
- **Identifiers (`profile/Pan.java:9` – `:27`; `profile/Tan.java:7` – `:19`):**
  - `Pan` checks `^[A-Z]{5}[0-9]{4}[A-Z]$` and masks as `value.substring(0, 3) + "XXXXX" + value.substring(8)`.
  - `Tan` checks non-blank only and masks as `value.substring(0, Math.min(3, value.length())) + "XXXXXXX"`.
  - Both are stored encrypted (`EncryptedField`); uniqueness is enforced through keyed digests (`IdentifierDigest.of(...)`) on `tax_profiles.pan_digest` and `tax_employers.{profile_id, tan_digest}` (`TaxProfileService.java:89`; `TaxProfileReferenceService.java:64`).
  - No response record exposes a raw PAN or TAN (`TaxpayerProfileResponse.java:12`; `FamilyMemberResponse.java:15`; `EmployerResponse.java:9`; `LandlordResponse.java:9`; `LenderResponse.java:7`).
- **Access logging:** `TaxProfileService.all` and `.read` log `ReadSubject.PROFILE`, and `TaxProfileReferenceService.familyMembers` logs `ReadSubject.FAMILY_MEMBERS`, because profiles and family records carry disability data (`TaxProfileService.java:39`, `:46`; `TaxProfileReferenceService.java:49`).
- **Reference ownership:** every create/read/replace in `TaxProfileReferenceService` first calls `taxProfileService.owned(email, profileId)`, and `replace` verifies `profileId.equals(record.getProfileId())` (`:139` – `:156`). `requireHeld(profileId, landlordIds, lenderIds)` checks that newly added draft references belong to the profile (`:110` – `:123`).

### Workspaces, drafts and revisions (`workspace/`)

- **Workspace & draft creation (`workspace/DraftService.java:52` – `:65`, `:101` – `:124`):** `workspace(...)` looks up `{profile_id, tax_year}` and creates it if absent. `createDraft(...)` requires a non-blank label, writes a `FilingDraft` at status `DRAFT` and revision `0` holding `DraftInputsMapper.blank()`, appends revision `0` with `fullStateJson` set to that blank document, and sets `workspace.primaryDraftId` if the workspace had none.
- **Draft document & strict binding (`workspace/DraftInputs.java:30` – `:114`; `workspace/DraftInputsMapper.java:24` – `:98`):**
  - `DraftInputs` has six components: `entered` (`EnteredFacts`), `filing` (`Filing`: `dueDate`, `furnishedOn`, `advanceTaxPaid`, `taxAtSource`, `selfAssessmentPaid`, `declarations`, `refundGrantedOn`, `ofWhichByEmployer`), `brought` (`LedgerSnapshot`), `thirdParties` (`landlordIds`, `lenderIds`), `evidence` (`EvidenceHeld`), `onboarding` (`Set<OnboardingStep>`: `PAY`, `PAY_STRUCTURE_AND_RENT`, `EMPLOYER_FUNDS`, `INVESTMENTS_AND_INSURANCE`, `OTHER_INCOME_AND_PROPERTY`; `OnboardingStep.java:8` – `:19`). Null `thirdParties`, `evidence`, `onboarding` default to `NONE` for backwards compatibility with older stored drafts (`DraftInputs.java:33` – `:37`).
  - `DraftInputsMapper.MAPPER` enables `FAIL_ON_UNKNOWN_PROPERTIES`, registers `@JsonTypeInfo(Id.SIMPLE_NAME)` mix-ins for `FairMarketValueBasis` (`ListedTraded`, `ListedNotTraded`, `MerchantBankerValue`) and `Declaration` (`YesNo`, `Amount`), and adds map key deserialisers for `ComponentCode`, `DeclarationCode`, and `TaxYearId` (`DraftInputsMapper.java:26` – `:68`).
- **JSON Patch (`workspace/JsonPatchApplier.java:14` – `:107`):** operates on a `deepCopy()` of the document. Accepts only an array of `add`, `remove`, and `replace` operations; unescapes RFC 6901 pointer tokens `~1` → `/` then `~0` → `~` (`:103`); supports array append with `-` on `add` (`:70` – `:73`) and numeric indices `0|[1-9][0-9]{0,8}` (`:91`).
- **Save checks and append-only revisions (`workspace/DraftService.java:170` – `:210`):**
  1. Draft must be owned and `status.isEditable()` (`DRAFT` or `READY`; `DraftStatus.java:25` – `:27`).
  2. `currentRevision` must equal `expectedRevision` from `If-Match`.
  3. Applies patch, runs `DraftInputsMapper.requireBinds`, binds `DraftInputs`.
  4. Checks only **newly added** landlord/lender IDs against `thirdPartyReferences.requireHeld(...)`, so IDs previously stored on a draft whose reference row was later purged by retention do not block unrelated edits (`:200` – `:210`).
  5. Validates `evidence.requireFits(entered, taxYear)`: each positional list (`receiptedMonths`, `journeyExpenditure`, and `ChapterViiiEvidence`'s six lists) must be empty or the exact length of its corresponding `/entered/...` list, with every count/amount in `[0, enteredBound]`, every `qualifyingPayments` code present in `/entered/qualifyingPayments` and `≤` the sum paid under that code, and `agniveerOwnSubscription` present only when `/entered/agniveerCorpusFund` is present (`EvidenceHeld.java:48` – `:91`; `ChapterViiiEvidence.java:46` – `:70`).
  6. Validates `/filing/declarations` against the year's catalogue via `taxCalculator.requireAsked(...)` (`DraftService.java:186` – `:187`).
  7. Inserts `DraftRevision` at `expectedRevision + 1` **before** saving `FilingDraft` (`:192` – `:196`), setting `reopened = true` if the draft was `READY` and moving status back to `DRAFT`.
- **Optimistic concurrency & retry (`workspace/DraftSaveRetry.java:27` – `:77`; `workspace/DraftService.java:285` – `:311`):**
  - Two concurrent saves at the same revision both attempt to insert `{draft_id, expectedRevision + 1}` into `tax_draft_revisions`. The loser hits `DataIntegrityViolationException` and rolls back.
  - `DraftSaveRetry` runs outside `@Transactional` with `maxRetries = 5`, initial delay `20 ms`, multiplier `2`, max delay `500 ms`, jitter `10 ms` on `DataIntegrityViolationException` (`DraftSaveRetry.java:33` – `:40`). On retry, once the winner commits, `DraftService.save` sees `currentRevision != expectedRevision` and computes `conflictingPaths` by collecting every operation `path` from revisions `> expectedRevision` and keeping incoming paths where `one.equals(other) || one.startsWith(other + "/") || other.startsWith(one + "/")` (`DraftService.java:290` – `:311`). If all retries still collide, it throws `conflictAfterRace` (409, empty `conflictingPaths`).
- **Replay (`workspace/DraftService.java:226` – `:240`):** `inputsAt(email, draftId, number)` loads all revisions ordered by `revisionNumber`, starts from `fullStateJson` (revision 0), and replays each `changeSetJson` up to `number`, logging `ReadSubject.DRAFT_REVISION`.
- **Status lifecycle and regime election (`workspace/DraftStatus.java:7` – `:28`; `workspace/FilingDraft.java:73` – `:79`; `workspace/DraftService.java:243` – `:269`):**
  - Allowed transitions: `DRAFT → {READY, ARCHIVED}`; `READY → {DRAFT, ACTED_ON, ARCHIVED}`; `ACTED_ON → {ARCHIVED}`; `ARCHIVED → {DRAFT}`.
  - Moving to `ACTED_ON` stamps `acted_on_at` (UTC); moving to `ARCHIVED` stamps `archived_at` (UTC) and leaving `ARCHIVED` clears `archived_at`.
  - When moving to `ACTED_ON` with non-null `regimeCode`, `DraftService` sets `draft.regimeElection` and upserts `RegimeElectionHistory` for `{profile_id, regime_code}`, appending `ElectionEvent(taxYearStart, draftId)` if no event already exists for `taxYearStart` (`RegimeElectionHistory.java:58` – `:62`).

### Salary workbook import (`workspace/importing/`)

- **Isolated reading (`workspace/importing/SalaryImportConfig.java:10` – `:16`; `SalaryStructureWorkbook.java:28` – `:124`):** untrusted `.xlsx` bytes (up to `MAX_BYTES = 1_048_576`, `SalaryImportService.java:49`) are handed to a child JVM (`IsolatedChild.of(SalaryStructureWorkbook.class, settings.isolation())`) with budget `settings.isolation().uploadReadBudget()` (`SalaryImportService.java:176`). The child opens the first sheet with Apache POI `XSSFWorkbook`, locates header columns `Component`, `Annual Amount`, and/or `Monthly Amount` (case- and whitespace-normalised), reads up to `MAX_ROWS = 200` non-blank rows (`SalaryStructureWorkbook.java:30` – `:34`, `:48` – `:90`), formats numeric/cached-formula/string/boolean cells as plain text (`:108` – `:119`), and writes one `WorkbookRows` JSON object to `FileDescriptor.out` (`:40` – `:46`). The parent deserialises the reply with `STRICT` (`FAIL_ON_UNKNOWN_PROPERTIES`, `FAIL_ON_NULL_FOR_PRIMITIVES`, `FAIL_ON_MISSING_CREATOR_PROPERTIES`, `FAIL_ON_NULL_CREATOR_PROPERTIES`; `SalaryImportService.java:51` – `:54`, `:186` – `:192`).
- **Preview (`workspace/importing/SalaryImportService.java:64` – `:100`, `:195` – `:216`):**
  - Looks up the draft's tax year (`drafts.ownedDraft`) and loads `taxCalculator.payLineComponents(year)`.
  - Indexes components by normalised `code` and `displayName` (`trim`, collapse whitespace to single space, lowercase `Locale.ROOT`).
  - Parses amounts by stripping commas and requiring `\d+(\.\d{1,2})?`. If `Annual Amount` is present it is used (`fromMonthly = false`); else if `Monthly Amount` is present it is multiplied by `12` (`fromMonthly = true`); else empty (`:195` – `:212`).
  - Rows with blank label, no matching component, or no amount go to `returned` with an explanation; matched rows with an amount go to `matched` and sum into `matchedTotal`. Nothing is stored.
- **Apply (`workspace/importing/SalaryImportService.java:109` – `:173`; `workspace/DraftService.java:162` – `:168`):**
  - Re-runs `preview` on the uploaded file.
  - Verifies every returned row is decided **exactly once** (either in `decisions.mappings()` or `decisions.leftOut()`, with no duplicates, stray rows, or undecided rows; `:146` – `:161`).
  - Verifies every mapped `code` exists in the year's components, every mapped row has an amount, no component code appears twice across matched + mapped rows, at least one pay line remains, and `decisions.employment()` does not already contain `"components"` (`:119` – `:132`, `:164` – `:173`).
  - Builds a one-op JSON Patch `[{"op": "add", "path": "/entered/employments/-", "value": <employment with components>}]` and a `WorkbookImport(sha256, rowsApplied, rowsMapped, rowsLeftOut)` (`:133` – `:140`).
  - Saves via `draftSaves.importInto(...)` (`DraftSaveRetry` → `DraftService.importInto`), which first refuses if any earlier revision of the draft already imported the same `workbook_sha256` (`DraftService.java:164` – `:166`).

### Assembly and portfolio bridge (`assemble/`, `portfolio/`)

- **`EngineInputsAssembler` (`assemble/EngineInputsAssembler.java:43` – `:117`):** the sole builder of `EngineInputs` in the app.
  - `assemble(email, draftId, regime, asOf)` reads the owned draft and profile, loads `CapitalGainsInput` via `CapitalGainsAdapter.inputFor(email, year)` only when `profile.isHoldsPortfolio()` is true (else `CapitalGainsInput.none(..., "not-the-portfolio-holder")`, `:62` – `:63`), builds `Assessee` (`Huf` or `Individual`, `:88` – `:94`), `FamilySnapshot` from `FamilyMemberRepository` (`:96` – `:106`), `RegimeElectionSnapshot` marking every `RegimeElectionHistory` with `isElectedBy(year)` as `ElectionState.IN_EFFECT` (`:108` – `:116`), and `FilingFacts` from `draft.filing().toFilingFacts()` (`:66`).
  - `forCapitalGains(profile, year, regime, asOf, gains)` builds inputs with empty entries/ledgers and `FilingFacts.notYetFurnished(year.endsOn())` for read-only capital-gains and held-asset views (`:74` – `:78`).
- **`CapitalGainsAdapter` (`portfolio/CapitalGainsAdapter.java:41` – `:99`):** calls `CapitalGainsSummaryService.summaryFor(email, new FinancialYear(year.startYear()).label())`, drops all-nil buckets (`:60` – `:71`), maps each bucket's `BucketFacts` (asset type, STT on acquisition/transfer, acquisition mode & corporate action, dates, venues — never portfolio's own term, `:74` – `:78`), and passes every coded charge plus non-zero `miscRupees` under code `"MISC"` (`:87` – `:94`), converting every rupee double via `MoneyBridge.fromTMoney`.
- **`MoneyBridge` (`portfolio/MoneyBridge.java:14` – `:44`):** one-way lossless bridge from canonical `double` rupees (`TMoney`) to exact `Money`: refuses NaN/infinite and any value with `BigDecimal.valueOf(v).stripTrailingZeros().scale() > 2` (`:26` – `:37`). `toTMoney(Money)` always throws `UnsupportedOperationException` (`:40` – `:43`).

### Computations, advisory views, reports and held assets (`compute/`)

- **Compute & store (`compute/DraftComputationService.java:84` – `:134`):** assembles inputs at `LocalDate.now(clock)`, runs `taxCalculator.compute(inputs)`, computes a `Band(bill, billWithRoomsFilled(inputs, openFamilies), coarseSentence)` whenever any of the 5 `OnboardingStep`s is unanswered (`:115` – `:121`), appends `BAND_NOT_INCLUDED` beside `NOT_INCLUDED` while coarse, saves `DraftComputation` (encrypted `result_json`, clear `law_and_exec`, `rule_checksums` of every trace rule read, UTC `computed_at`), and calls `draftService.clearStaleness(draftId)`.
- **First figure (`compute/DraftFirstFigureService.java:41` – `:83`):** creates a draft, applies one patch adding a single `SPECIAL_ALLOWANCE` employment with `grossPay` (`employerKind = "OTHER"`), `/filing/dueDate`, and `OnboardingStep.PAY` answered (`:67` – `:82`), calls `compare`, then computes and stores every compared regime (chosen first, deduplicated).
- **Compare regimes, choose form, optimise (`compute/DraftComputationService.java:138` – `:186`; `HrSupportTable.java:23` – `:87`; `OptimiserSettings.java:15` – `:37`):**
  - `compare` runs `taxCalculator.compareRegimes` under the year's default regime and labels the answer coarse when any onboarding step is unanswered.
  - `chooseForm` delegates to `taxCalculator.chooseForm(inputs, new ReturnKind(returnKind))`.
  - `optimise` enforces `verificationBreadth <= app.tax.optimiser.exhaustive-breadth` (`OptimiserSettings.breadthFor`), runs `taxCalculator.optimise` under the default regime, and joins each changed pay component and employer fund with its `HrSupportLikelihood` (`HIGH`, `MEDIUM`, `LOW`, `EMPLOYER_DISCRETION`, `NOT_APPLICABLE`) loaded at startup from `data/tax-planning/hr-support.json` (`DraftOptimisation.java:24` – `:31`; `HrSupportConfig.java:10` – `:13`).
- **Advisory views (all read-only, stored nowhere; regime is `draft.regimeElection` if set, else `taxCalculator.defaultRegime(year)`, `DraftForecastService.java:55` – `:59`):**
  - **Forecast (`DraftForecastService.java:43` – `:83`):** computes `liability = bill + Σ interestAndFees`, `otherCredits = credits − byEmployer`, `rounding = finalAmount − (liability − byEmployer − otherCredits)`, `settlement` (`PAYABLE_NOW` / `REFUND_AFTER_FILING` / `NIL`), and `payableBy` (earliest advance-tax instalment `≥ asOf` with `stillOwed > 0`, else `dueDate`).
  - **Calendar (`DraftCalendarService.java:48` – `:164`):** measures every cost as of `measuredAsOf = max(dueDate, today)`. Produces dated entries sorted by date for: each `ADVANCE_TAX_INSTALMENT` (`costOfNotPaying` by adding a payment on the instalment date), `DEFERMENT_RELIEF_PAID_BY` (when `reliefPaidBy` is present), each positive-saving `DEDUCTION_PAID_BY` on `taxYearEnd` (`priceDeductionRoom`), each cheaper non-elected `REGIME_ELECTION` with a deadline (`regimeElection`), and `RETURN_DUE` (cost vs on-time filing, or 1 day late when not yet furnished and before due date).
  - **Completeness & Onboarding (`DraftCompletenessService.java:48` – `:86`; `DraftOnboardingService.java:49` – `:96`):** `completeness` merges `priceDeductionRoom` and `priceRentRoom`, drops zero-worth items, sorts descending by `worth` then `rule`, surfaces the top `SURFACED = 5` with their sum and the remainder. `onboarding` lists all 5 `OnboardingStep`s in enum order, attaching `rentRoom` to open `PAY_STRUCTURE_AND_RENT`, `deductionRooms` to open `INVESTMENTS_AND_INSURANCE`, and `NOT_MEASURED` text to open `PAY`, `EMPLOYER_FUNDS`, and `OTHER_INCOME_AND_PROPERTY`, plus `next` = first unanswered step.
  - **Claims (`DraftClaimsService.java:50` – `:103`):** returns `claimed` (`priceClaims` with `FinalAmount` and step dates resolving `TAX_YEAR_END` / `RETURN_DUE`) and `notClaimed` (remaining `claimPlaybooks`, unmeasured).
  - **Claim integrity & Document checklist (`DraftClaimIntegrityService.java:58` – `:170`; `EvidencedFacts.java:28` – `:153`; `ChapterViiiCuts.java:24` – `:137`; `ParticularsOnFile.java:18` – `:38`; `DraftDocumentChecklistService.java:57` – `:164`):**
    - `EvidencedFacts` + `ChapterViiiCuts` build modified `EnteredFacts` cutting each entered family to its held evidence (tenancy rent apportioned by `receiptedMonths / monthsOccupiedIn(taxYear)`, journey shares apportioned in paise with last traveller taking the remainder, qualifying payments filled in order up to the code's evidenced total, donations held to zero omitted).
    - `claimIntegrity` runs the draft as-is, with all evidence cut (`supported`), and once per entered `EvidenceKind` held to nothing to discover which `EXEMPTION`/`DEDUCTION` rules each kind affects, reporting `allowed`, `evidenced`, `gap`, `limitedBy` and pack sentences (plus landlord particulars only when a computation notice names the rule).
    - `documentChecklist` maps every claimed playbook's `guidance.evidence()` records (`RENT_RECEIPTS`, `JOURNEY_EXPENDITURE`, `QUALIFYING_PAYMENT`, `HEALTH_PAYMENT`, `DONATION`, `DEPENDANT_SUPPORT`, `LANDLORD_PARTICULARS`, `LENDER_PARTICULARS`, `LENDER_CERTIFICATE`, `NOT_RECORDED`) to `HELD` / `NOT_HELD` / `NOT_ASKED` / `NOT_RECORDED`.
  - **Reconciliation (`DraftReconciliationService.java:65` – `:116`):** gated by `DocumentSwitch.requireEnabled(settings)` (503 when off); selects stored documents of the draft's profile & tax year whose `CertificateLayout` defines comparisons, takes each document's latest confirmed parse attempt, logs `ReadSubject.DOCUMENT` per used certificate, runs `EmployerAssembly.assemble` and `Reconciliation.reconcile` against a fresh computation, and lists unconfirmed document IDs.
- **Capital-gains view & Held assets (`CapitalGainsViewService.java:35` – `:56`; `HeldAssetsViewService.java:46` – `:123`; `HeldAssetsWorkbook.java:13` – `:43`):**
  - Both require `profile.isHoldsPortfolio()`.
  - `CapitalGainsViewService` computes realised buckets using `readOn = year.startsOn().plusMonths(5)` (1 September) and optionally delegates held lots to `HeldAssetsViewService.lots(...)`.
  - `HeldAssetsViewService` reads every `AssetEntity` of the user with `quantity > 0`, calls `taxCalculator.termIfSold` as if sold on `asOf` (default today), checks `decidedLater` when `longTermFrom > year.endsOn()` (re-evaluating in the later year's pack or emitting a note if no pack ships for it), turns per-lot `IllegalArgumentException` into a lot note instead of failing the request, filters by optional `term`, and totals quantities per listed stock across all lots of that stock. `HeldAssetsWorkbook` exports the exact two tables as sheets `LOTS` and `TOTALS`.
- **PDF report (`ComputationReportService.java:35` – `:171`):** loads the stored `DraftComputationView` (via `byReceipt`, which verifies ownership and logs `COMPUTATION` read) and its stored `rule_checksums`. Never recomputes figures: groups stored trace steps by `kind` in first-seen order, marks each line with `RULE_NOT_RECORDED` / `RULE_CHANGED_SINCE` / `NOT_VERIFIED` by comparing pinned vs current rule checksums and verification levels (`:156` – `:170`), adds `packNote` when `lawAndExec` differs from current `packVersions` and `coarseNote` when `coarse` is true, fills the year's `DISCLAIMER` with `InstrumentCitations`, processes Thymeleaf template `templates/taxplanning/computation-report.html`, and renders PDF via Flying Saucer `ITextRenderer` with embedded `fonts/NotoSans-Regular.ttf` and `NotoSans-Bold.ttf`.

### Worked examples (by hand)

1. **PAN and TAN masking (`profile/Pan.java:20` – `:22`; `profile/Tan.java:16` – `:18`):**
   - `ABCDE1234F`: first 3 = `ABC`, middle 5 replaced by `XXXXX`, from index 8 = `4F` → **`ABCXXXXX4F`** (matches `PanTest.java:16`).
   - `BLRA12345B`: first 3 = `BLR`, followed by `XXXXXXX` → **`BLRXXXXXXX`**.
2. **Concurrent patch conflict-path overlap (`workspace/DraftService.java:290` – `:311`):**
   - Draft is at rev 0. Save A commits rev 1 with patch paths `/entered/employments/0/components/0/annualAmount` and `/filing/taxAtSource`.
   - Save B arrives with `If-Match: "rev-0"` and patch paths `/entered/employments/0` and `/filing/dueDate`.
   - `overlap("/entered/employments/0", "/entered/employments/0/components/0/annualAmount")` is `true` (`other.startsWith(one + "/")`); `overlap("/filing/dueDate", …)` is `false` for both rev-1 paths.
   - Answer: HTTP **409**, `ETag: "rev-1"`, message `"This draft has moved on to revision 1"`, `conflictingPaths = ["/entered/employments/0"]`.
3. **Salary workbook preview (`workspace/importing/SalaryImportService.java:72` – `:100`, `:195` – `:212`):**
   - Header: `Component | Annual Amount | Monthly Amount`.
   - Row 2: `Basic Pay | (blank) | 1,00,000` → strips commas (`100000`), monthly × 12 = **`1200000`** (`fromMonthly = true`), matched.
   - Row 3: `HRA | 4,80,000 | 40,000` → annual column wins → **`480000`** (`fromMonthly = false`), matched.
   - Row 4: `Special Bonus | 50,000 | (blank)` → no component named `special bonus` → `returned` with `annualAmount = 50000`.
   - `matchedTotal` = 12,00,000 + 4,80,000 = **`16,80,000`**.
4. **MoneyBridge canonical-paise guard (`portfolio/MoneyBridge.java:26` – `:37`):**
   - `145.38` → `BigDecimal.valueOf(145.38)` has scale 2 (`≤ 2`) → `Money.ofExact(145.38)`.
   - `145.37999999999997` → scale 14 (`> 2`) → refused with `IllegalArgumentException` rather than silently rounded.
5. **Forecast identity (`compute/DraftForecastService.java:62` – `:75`):**
   - T13-1 bill `87,750`, deferment interest `228`, `byEmployer = 75,000`, `otherCredits = 0`, `finalAmount` rounded to nearest ₹10 (`87,750 + 228 − 75,000 = 12,978` → `12,980`):
     `liability = 87,978`, `unrounded = 12,978`, `rounding = 12,980 − 12,978 = +2`, `net = 12,980`, `settlement = PAYABLE_NOW`.
     Check: `87,978 − 75,000 − 0 + 2 = 12,980` (`DraftForecastServiceTest`).

## 6. Configuration

Properties read by parts one and two (shipped identically across `application.yaml`, `application-staging.yaml` and `application-prod.yaml` unless noted):

| Property / constant | Shipped value | Where |
|---|---|---|
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `21MB` / `22MB` | `backend/src/main/resources/application.yaml:9` – `:11` |
| `spring.jackson.deserialization.fail-on-unknown-properties` | `true` | `backend/src/main/resources/application.yaml:18` – `:22` |
| `spring.data.web.pageable.*` (`serialization-mode` / `default-page-size` / `max-page-size`) | `via-dto` / `50` / `200` (applies to `GET …/drafts/{draftId}/revisions`) | `backend/src/main/resources/application.yaml:25` – `:29` |
| `app.tax.documents.isolation.*` (`heap-mebibytes` / `cpu-seconds` / `upload-read-budget` / `max-reply-bytes`) | `256` / `30` / `PT10S` / `16777216` (shared by `SalaryImportConfig`) | `application.yaml:151` – `:156`; `application-staging.yaml:126` – `:131`; `application-prod.yaml:126` – `:131`; `workspace/importing/SalaryImportConfig.java:13` – `:15` |
| `SalaryImportService.MAX_BYTES` | `1_048_576` (1 MiB, Java constant) | `workspace/importing/SalaryImportService.java:49` |
| `SalaryStructureWorkbook.MAX_ROWS` | `200` (Java constant) | `workspace/importing/SalaryStructureWorkbook.java:34` |
| `DraftSaveRetry.ON_COLLISION` | `maxRetries=5`, `delay=20ms`, `multiplier=2`, `maxDelay=500ms`, `jitter=10ms` | `workspace/DraftSaveRetry.java:33` – `:40` |
| `app.tax.optimiser.exhaustive-breadth` | `6` (must be `≥ 1`) | `application.yaml:132` – `:134`; `application-staging.yaml:107` – `:109`; `application-prod.yaml:107` – `:109`; `compute/OptimiserSettings.java:14` – `:22` |
| `data/tax-planning/hr-support.json` | 23 component/fund `HrSupportLikelihood` entries (loaded at startup) | `backend/src/main/resources/data/tax-planning/hr-support.json:1` – `:28`; `compute/HrSupportTable.java:26` |
| `templates/taxplanning/computation-report.html` + `fonts/NotoSans-{Regular,Bold}.ttf` | PDF report template and embedded rupee-capable fonts | `compute/ComputationReportService.java:38`, `:136` – `:142` |
| `DraftCompletenessService.SURFACED` | `5` (top surfaced items, Java constant) | `compute/DraftCompletenessService.java:29` |

## 7. Tests

### Part one (0 plan or review citations)

**Unit tests** (`backend/src/test/java/com/thiru/wealthlens/taxplanning/`):
- `profile/PanTest` (2): PAN masking (`ABCXXXXX4F`) and malformed PAN refusals without echoing the value.
- `workspace/JsonPatchApplierTest` (3): RFC 6902 `add`/`remove`/`replace`, array append `-`, invalid ops and out-of-range refusals.
- `workspace/EnteredFactsBindingTest` (9): strict `DraftInputsMapper` binding to `EnteredFacts` and `FairMarketValueBasis` subtypes.
- `workspace/DeclarationsBindingTest` (5): `/filing/declarations` JSON round-trip (`YesNo`, `Amount`) and key deserialisation.
- `workspace/DraftFilingTest` (5): `/filing/ofWhichByEmployer` bounds against `/filing/taxAtSource`.
- `workspace/EvidenceHeldTest` (8) and `workspace/ChapterViiiEvidenceTest` (5): positional length matching, bounds against entered facts, and missing-entry refusals.
- `workspace/DraftSaveRetryTest` (4): retry on `DataIntegrityViolationException`, immediate propagation of non-collision failures, exhaustion to `conflictAfterRace`.
- `workspace/importing/SalaryStructureWorkbookTest` (6) + fixture helper `Workbooks`: child-process POI reading, headers, formulas, blank rows, `MAX_ROWS` refusal.
- `workspace/importing/SalaryImportServiceTest` (12): real child-process preview and apply, monthly annualisation, returned rows, decision checks, duplicate-component refusal.

**Integration tests** (`backend/src/test/java/com/thiru/wealthlens/integration/`):
- `TaxProfileIntegrationTest` (13): profile and all five reference CRUD routes over HTTP + Mongo, ownership isolation, PAN/TAN masking, unique PAN/TAN digests, `holdsPortfolio` uniqueness, access log writes.
- `DraftIntegrationTest` (40): workspaces, draft CRUD, `ETag`/`If-Match` concurrency and conflict paths, revision pagination and replay, status transitions, `RegimeElectionHistory`, declarations, evidence and third-party checks.
- `SalaryImportIntegrationTest` (7): multipart preview and apply over HTTP, `If-Match` conflict, duplicate workbook SHA-256 refusal, `WorkbookImport` on the revision.
- `RetiredSalaryFlowIntegrationTest` (2): confirms the retired `/tax-planning/**` paths answer 404.
- `StoredActorEncryptionIntegrationTest` (3): confirms stored actor fields (`changed_by`, `audit_metadata`) are encrypted.

### Part two (6 plan citations re-homed by W-357b10b; `plans/W-088g9.md` deleted)

**Unit tests** (`backend/src/test/java/com/thiru/wealthlens/taxplanning/`):
- `portfolio/` (8 classes, 48 tests): `MoneyBridgeTest` (6), `CapitalGainsAdapterTest` (10), `CapitalGainsNettingTest` (1), `ChargeCatalogueCoverageTest` (2), `LandOrBuildingCapTest` (12; cites §11 W-088g), `LandOrBuildingCapitalGainsTest` (6; cites §11 W-088g2), `LandSaleNoticeTest` (5), `PropertySaleCapitalGainsTest` (7; cites §11 W-088g9).
- `compute/` (25 classes, 107 tests): `ClaimDeadlineCalendarTest` (1), `ComputationReportServiceTest` (8), `ComputedTaxJsonTest` (3), `DraftBandTest` (3), `DraftCalendarBenchmarkTest` (1), `DraftCalendarServiceTest` (13; cites `taxengine.md` §11 W-089), `DraftClaimIntegrityServiceTest` (11), `DraftClaimsServiceTest` (4), `DraftCompletenessBenchmarkTest` (1), `DraftCompletenessServiceTest` (2), `DraftDocumentChecklistServiceTest` (8), `DraftFirstFigureServiceTest` (2), `DraftForecastServiceTest` (6; cites `taxengine.md` §11 W-089), `DraftOnboardingServiceTest` (4), `DraftOptimisationRequestTest` (8), `DraftOptimisationTest` (1), `ForbiddenWordingTest` (2), `HeldAssetsViewServiceTest` (4), `HeldAssetsWorkbookTest` (1), `HrSupportCoverageTest` (1), `HrSupportTableTest` (8), `NotIncludedWaitsTest` (2), `OptimiserSettingsTest` (5), `StoredComputationReadTest` (1), `T12ClaimIntegrityPanelTest` (6).

**Integration tests** (`backend/src/test/java/com/thiru/wealthlens/integration/`):
- `DraftComputationIntegrationTest` (62; cites `taxengine.md` §11 W-089), `ComputationReportIntegrationTest` (6), `HeldAssetsIntegrationTest` (5), `DraftReconciliationIntegrationTest` (5).

## 8. Known gaps (W-395 – W-396)

**Boundary, profiles, workspaces, drafts and salary import (W-395):**
1. **`POST /api/v1/me/tax/profiles/{profileId}/workspaces/{taxYear}/drafts` takes `Map<String, String>`** (`workspace/controller/DraftController.java:56` – `:58`). Because it binds a map rather than a typed request record, `spring.jackson.deserialization.fail-on-unknown-properties` is bypassed (extra keys beside `label` are silently dropped) and `@Valid` validates nothing.
2. **`regimeCode` on `POST /api/v1/me/tax/drafts/{draftId}/status` is never validated** (`workspace/DraftService.java:249` – `:268`; `workspace/DraftStatusRequest.java:4`). When `status` is `ACTED_ON`, any non-null string — including blank text or a code no shipped pack defines — is stored on `FilingDraft.regimeElection` and persisted into `tax_regime_election_history`.
3. **Constraint-free request records and missing `@Valid` on `SalaryImportDecisions`:** `SalaryImportController.java:41` omits `@Valid` on `@RequestPart("decisions") SalaryImportDecisions decisions`, and the seven profile and draft-status request records (`TaxpayerProfileRequest`, `FamilyMemberRequest`, `EmployerRequest`, `LandlordRequest`, `LenderRequest`, `BankAccountRequest`, `DraftStatusRequest`) carry no Bean Validation annotations even though their controllers annotate them `@Valid` (validation instead happens in `applyTo` / `changeStatus`).
4. **Shared `DuplicateKeyException` message on all five reference writes** (`profile/TaxProfileReferenceService.java:158` – `:164`): `save(...)` translates every `DuplicateKeyException` to `"This profile already holds an employer with that TAN"`, even for family members, landlords, lenders and bank accounts.
5. **Untrue Javadoc:**
   - `profile/controller/TaxProfileController.java:19` and `profile/controller/TaxProfileReferenceController.java:27` still cite `UserValidator` and "the path's email", both removed in W-358a / W-358c1.
   - `workspace/FilingDraft.java:20` says "`inputsJson` is `EnteredFacts` as JSON text", whereas it stores `DraftInputs` (`workspace/DraftInputs.java:30` – `:31`).
6. **`enums/EmployerType` duplicates `taxengine.input.EmployerKind`:** `enums/EmployerType.java:3` – `:7` (`GOVT`, `PSU`, `PRIVATE`) is the legacy form still stored on `tax_employers.type` and served by `EmployerRequest` / `EmployerResponse` (`taxengine/input/EmployerKind.java:7`).
7. **25 of part one's 31 routes have no `api-collection` request** (only `POST …/profiles`, `POST …/drafts`, `GET …/declarations/{taxYear}`, `PATCH …/drafts/{draftId}`, and the two `salary-import` routes exist under `api-collection/Tax Profile/`).
8. **`tax_profile_erasures` has no declared index and is omitted from `TaxProfileIndexInitializer`** (`profile/ProfileErasure.java:22` – `:45`; `profile/TaxProfileIndexInitializer.java:22` – `:23`), although `ProfileErasureRepository.java:10` – `:14` queries by `profileId` + `erasedAt`, `erasedAt`, and `requestedAt`.

**Assembly, portfolio bridge, computation, advisory views and report (W-396):**
9. **`POST /api/v1/me/tax/drafts/{draftId}/compute` and `POST /api/v1/me/tax/drafts/{draftId}/itr-form` take `Map<String, String>`** (`compute/controller/DraftComputationController.java:70` – `:74`, `:127` – `:131`), bypassing `fail-on-unknown-properties` and leaving `@Valid` empty (same shape as gap 1).
10. **`DraftComputationService.computations(...)` is `@Transactional(readOnly = true)` while writing access logs** (`compute/DraftComputationService.java:194`, `:199` – `:213`): `listing(...)` writes `taxAccessLog.record(...)` (`:210`) inside a read-only transaction.
11. **Constraint-free request records:** `DraftFirstFigureRequest` (`compute/DraftFirstFigureRequest.java:13`) and `DraftOptimisationRequest` (`compute/DraftOptimisationRequest.java:30` – `:32`) carry no Jakarta Bean Validation constraints though `DraftComputationController` annotates both `@Valid` (`:78`, `:135`).
12. **Repeated draft/profile reads per advisory request:** eight services (`DraftForecastService.java:44` – `:47`, `DraftCalendarService.java:49` – `:51`, `DraftClaimIntegrityService.java:59` – `:61`, `DraftClaimsService.java:51` – `:53`, `DraftCompletenessService.java:49` – `:51`, `DraftDocumentChecklistService.java:58` – `:60`, `DraftOnboardingService.java:50` – `:52`, `DraftReconciliationService.java:67`, `:81`) call `draftService.ownedDraft` and then immediately call `assembler.assemble`, which calls `draftService.ownedDraft` and `taxProfileService.owned` again.
13. **`CapitalGainsViewService.view` pins `readOn` to 1 September (`year.startsOn().plusMonths(5)`)** (`compute/CapitalGainsViewService.java:42`), rather than reading the clock or the year's end, so a mid-year law change after 1 September in that year would be evaluated at 1 September.
14. **One route in part two has no `api-collection` request:** `GET /api/v1/me/tax/computations/{receipt}` (`compute/controller/DraftComputationController.java:145` – `:148`).
15. **Stale / stacked Javadoc and wording:**
    - `compute/DraftComputationService.java:188` – `:198` has two stacked Javadoc blocks separated by `@Transactional(readOnly = true)`.
    - `compute/DraftCalendar.java:48` says `"80D and own NPS are W-114c"` (W-114c and W-114d are done).
    - `compute/DraftComputation.java:60` – `:62` says `"the audit's created-at is never filled for an entity whose id is set before saving"` (`CreatedWithId` / `newlyCreated` now marks it new, `:70` – `:78`).
    - `compute/DraftClaimsService.java:35` still states `"No Form 16 is compared yet"` (Form 16 was removed by W-126e5 / D-748 for TY 2026-27+, where Form No. 130 is reconciled).

## 9. Not covered here

- **Parts three and four of `taxplanning`** (to be added in `W-357b10c` – `d`): `documents/` and `isolation/`, `protection/`, `erasure/`, `jobs/`, `gazette/` and admin routes.
- **Tax rules, nodes, inputs, rule packs, filing forms and optimiser:** `docs/modules/taxengine.md`.
- **Portfolio lots, trade outcomes and capital-gains summary:** `docs/modules/portfolio.md`.
- **Zones, `@CallerEmail`, `ProblemDetail`, `EncryptedField` / `SearchableIdentifier`, whole-account erasure:** `docs/modules/shared.md`.
- **Sign-in, tokens and roles:** `docs/modules/auth.md`.

## 11. Hand-worked figures the tests cite

Moved verbatim from the plans cited by `taxplanning`'s tests (W-357b10b, C1a; same convention as `docs/modules/taxengine.md` §11). `W-089`'s T13-1 working already lives in `docs/modules/taxengine.md` §11 (W-089), where `DraftCalendarServiceTest`, `DraftForecastServiceTest` and `DraftComputationIntegrationTest` now point. `plans/W-088g9.md` had no other non-history citation and was deleted (readable at `1f5fce4b`); `plans/W-088g.md`, `plans/W-088g2.md` and `plans/W-089.md` stay while other kept plans (`W-088g1/g2/g4/g5`, `W-112b`) still cite them.

### W-088g — land or building acquired before 23-7-2024

Cited by `taxplanning/portfolio/LandOrBuildingCapTest.java`.

#### The numbers I will use, and where each comes from

| Number | Value | Source |
|---|---|---|
| the rate on the gain | 12.5 % | 2025 s.197(1)(b) (Gazette `265620`, p.221); 1961 s.112(1)(a)(ii)(B) as substituted (Gazette `256436`, pp.26–27) |
| the rate on indexed cost | 20 % | 2025 s.197(3) (p.221); 1961 s.112(1)(a) pre-2024 (thc `it1961`, p.462) and the substituted second proviso (p.26) |
| the acquisition date | before 23-7-2024 | both, as above |
| the floor of the divisor | tax year 2001-02 | 2025 s.72(8)(b) (p.118); 1961 s.48 Explanation (iii) (p.273) |
| CII 2001-02 … 2026-27 | 100, 105, 109, 113, 117, 122, 129, 137, 148, 167, 184, 200, 220, 240, 254, 264, 272, 280, 289, 301, 317, 331, 348, 363, 376, 384 | `sources/2026-10-31-cost-inflation-index.md`, six hashed Gazette copies (D-424) |
| the CII used by the goldens | 254 = 2015-16, 117 = 2005-06, 363 = 2024-25, 376 = 2025-26, 384 = 2026-27 | the same table |

Each of the five goldens is computed by hand as `cost × index(sale year) ÷ index(max(acquired year, 2001-02))`, rounded
once to paise; `A = gain × 12.5 %`, `B = indexed gain × 20 %`; the charge is the lesser. All five were re-derived this
session with `decimal` at half-up to paise, matching `Money.CALC` (12 significant digits, `HALF_UP`, `Money.java:33`) and
`Rate.of`'s single rounding (`Rate.java:44-46`). The harness gives ₹10,00,000 of other income, above the nil band ceiling
of the first-schedule table (₹2,50,000) and of the new regime's (₹4,00,000), so the s.197(2) shift is nil and the goldens
isolate the cap.

| # | TY | acquired | cost → consideration | the two measures | A (12.5 %) | B (20 %) | charged | what it proves |
|---|---|---|---|---|---|---|---|---|
| 1 | 2025 | 2015-06-01 | 40,00,000 → 1,00,00,000 (CII 254 → 376) | gain 60,00,000; indexed cost 59,21,259.84, indexed gain 40,78,740.16 | 7,50,000 | 8,15,748.03 | **7,50,000** | the cap is inert where indexation helps little — the W-088g2 golden's figure is unchanged |
| 2 | 2025 | 2005-06-01 | 10,00,000 → 40,00,000 (117 → 376) | gain 30,00,000; indexed cost 32,13,675.21, indexed gain 7,86,324.79 | 3,75,000 | 1,57,264.96 | **1,57,264.96** | the cap binds — today the engine charges 3,75,000 |
| 3 | 2026 | 2005-06-01 | 10,00,000 → 40,00,000 (117 → 384) | gain 30,00,000; indexed cost 32,82,051.28, indexed gain 7,17,948.72 | 3,75,000 | 1,43,589.74 | **1,43,589.74** | the same case in the second pack, with a different sale-year index |
| 4 | 2026 | 2024-07-22 | 10,00,000 → 11,00,000 (363 → 384) | gain 1,00,000; indexed cost 10,57,851.24, indexed gain 42,148.76 | 12,500 | 8,429.75 | **8,429.75** | one day before the date — qualifies |
| 5 | 2026 | 2024-07-23 | 10,00,000 → 11,00,000 | no measure; gain 1,00,000 | 12,500 | 20,000 | **12,500** | the day itself is not "before" — the cap cannot bind |
| 6 | 2025 | 2005-06-01, non-resident | 10,00,000 → 40,00,000 | no measure; gain 30,00,000 | 3,75,000 | 6,00,000 | **3,75,000** | s.197(3) is resident-only; the qualifier's conditions do the work |

### W-088g2 — a land or building capital asset

Cited by `taxplanning/portfolio/LandOrBuildingCapitalGainsTest.java`.

- through `portfolio`: a `trade_outcome` row with `AssetType.LAND_OR_BUILDING`, bought 1-6-2015 for 40,00,000, sold
  1-9-2025 for 1,00,00,000, **no charge breakdown** (so both STT sides read UNKNOWN), through
  `CapitalGainsSummaryService` → `CapitalGainsAdapter` → `CapitalGainsNode`: part `LONG_TERM_OTHER`, gain 60,00,000;
- through `TaxByBasisNode` with that head: one special-rate line naming `RATE-CG-LONG-OTHER` at 12.5 % — 7,50,000;
- a second row bought 10-1-2025, sold 1-9-2025 (7 months): part `SHORT_TERM_SLAB`, no special-rate line;
- boundary pair (D-425 deviation 1): 1-9-2023 → 1-9-2025 (exactly 24 calendar months → short-term, D-303) against
  1-8-2023 → 1-9-2025 (25 months → long-term).

### W-088g9 — a property entry of its own

Cited by `taxplanning/portfolio/PropertySaleCapitalGainsTest.java`.

#### Tests (figures by hand)

**Case A, long-term.** Bought 1-6-2015 for 40,00,000, with stamp duty 2,80,000, registration 40,000 and brokerage 40,000
(expenses 3,60,000). Sold 1-9-2025 for 1,00,00,000, with brokerage 1,00,000 and legal fee 20,000 (1,20,000).

```
Gain            1,00,00,000 − 1,20,000 − 40,00,000 − 3,60,000       = 55,20,000     (LONG_TERM_OTHER)
At 12.5 %       55,20,000 × 12.5 %                                  = 6,90,000.00
Indexed cost    43,60,000 × 376 ÷ 254                               = 64,54,173.23
Indexed gain    1,00,00,000 − 1,20,000 − 64,54,173.23               = 34,25,826.77
At 20 %         34,25,826.77 × 20 %                                 = 6,85,165.35
Tax charged     the lesser (s.197(3))                               = 6,85,165.35
```

**Case B, short-term.** Bought 10-1-2025 for 20,00,000 with stamp duty 1,00,000; sold 1-9-2025 for 25,00,000 with
brokerage 50,000. Gain 25,00,000 − 50,000 − 21,00,000 = **3,50,000**, in `SHORT_TERM_SLAB`.
