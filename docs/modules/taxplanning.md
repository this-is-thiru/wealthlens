# Module `taxplanning` — profiles, drafts, computations, documents and tax operations

> Written from the code at `review/taxplanning`; every claim cites the line it was read from, and a bare path is under
> `backend/src/main/java/com/thiru/wealthlens/taxplanning/`. Template: `docs/taxplanning-build/plans/W-357b.md` C2a. The module is
> written in four parts (D-923): boundary, profiles, workspaces, drafts and salary import (W-357b10a, D-924); assembly, portfolio
> bridge, computation, advisory views and report (W-357b10b, D-925); documents and isolation (W-357b10c, D-926); protection, erasure,
> jobs and the Gazette watch (W-357b10d, D-927). The doc is complete.

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

Every route in parts one to three, and the erasure route of part four, is in the `me` zone (`@MeApi`, prefix `/api/v1/me/tax`), taking the authenticated user from `@CallerEmail`; part four's other three are admin routes.

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

### Documents (`documents/controller/`)

All three answer **503** `"Uploading documents is switched off (app.tax.documents.enabled=false)"` while `app.tax.documents.enabled` is `false`, which all three profile yamls ship (`documents/DocumentSwitch.java:12` – `:21`; §6).

| Method and path | Body / params → answer | Where |
|---|---|---|
| `POST /api/v1/me/tax/documents` | multipart `file`, `profileId`, `taxYear` (`int`, the start year), `documentType` (`DocumentType`) → `DocumentView(documentId, profileId, taxYear "2026-27", documentType, pages, uploadedAt, newlyStored)`; the same bytes under the same profile answer the stored document with `newlyStored = false` | `documents/controller/DocumentController.java:32` – `:36`; `documents/DocumentService.java:69` – `:95`; `documents/DocumentView.java:15` – `:22` |
| `GET /api/v1/me/tax/documents/{documentId}/parse` | → `ParseView(documentId, state WAITING / READ, reading, employeePan MATCHES / DIFFERS / NOT_READ, confirmed)`; `reading` is the latest attempt's `(attemptId, outcome, fields, unmappedText, notes)`; records a `DOCUMENT` read | `documents/controller/DocumentController.java:38` – `:41`; `documents/DocumentService.java:97` – `:102`, `:129` – `:139`; `documents/ParseView.java:16` – `:60` |
| `POST /api/v1/me/tax/documents/{documentId}/confirm` | `@Valid ConfirmationRequest(attemptId, accepted, corrected, rejected)` (a list or map left out is empty) → `ParseView` with `confirmed = true` | `documents/controller/DocumentController.java:43` – `:46`; `documents/DocumentService.java:104` – `:127`; `documents/ConfirmationRequest.java:12` – `:18` |

### Erasure, jobs and the Gazette watch (`erasure/controller/`, `controller/`)

The erasure route is in the `me` zone. The other three are in the `admin` zone (`@AdminApi`, prefix `/api/v1/admin/tax`) and also carry `@PreAuthorize("hasRole('SUPER_USER')")`.

| Method and path | Body / params → answer | Where |
|---|---|---|
| `POST /api/v1/me/tax/profiles/{profileId}/erasure` | → **202** `ErasureReceipt(profileId, requestedAt, eraseOn, erased, heldUntilThen, contact)`; the profile is held at once and erased by the retention purge once `app.privacy.erasure-hold` has passed | `erasure/controller/ErasureController.java:27` – `:31`; `erasure/ErasureService.java:30` – `:40`; `erasure/ErasureReceipt.java:12` – `:26` |
| `GET /api/v1/admin/tax/gazette-leads` | `Pageable` → `PagedModel<GazetteLeadResponse>`, newest `foundAt` first, the id breaking a tie | `controller/GazetteWatchController.java:29` – `:33`; `gazette/GazetteWatchService.java:30` – `:33` |
| `PUT /api/v1/admin/tax/gazette-cursor` | `@Valid GazetteCursorRequest(year, lastNumber)` → `GazetteCursorResponse` (the cursor, `updatedBy` = the caller) | `controller/GazetteWatchController.java:36` – `:40`; `gazette/GazetteWatchService.java:41` – `:58` |
| `POST /api/v1/admin/tax/jobs/{name}/run` | → `JobRunReceipt(job, ran)`; `ran = false` when another run holds the job's lease | `controller/TaxJobController.java:21` – `:25`; `jobs/TaxJobDirectory.java:28` – `:37`; `jobs/JobRunReceipt.java:9` |

### Refusals (all parts)

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
| Documents switched off | **503** `"Uploading documents is switched off (app.tax.documents.enabled=false)"` | `documents/DocumentSwitch.java:12`, `:17` – `:21` |
| Upload of a form no parser reads (`FORM_16`, `FORM_122`, `FORM_123`), or a year whose pack has no layout of the form | 400 `"No parser reads <type> yet"` / `"<type> has no layout for tax year <label>"` | `documents/DocumentService.java:73` – `:76` |
| Upload larger than `max-bytes`; malware found; not starting `%PDF-`; password-protected; unopenable; no pages; more than `max-pages` | 400 `"A document is at most 20971520 bytes"` / `"This file failed the malware scan and was not stored"` / `"Only a PDF can be uploaded"` / `"The PDF is protected by a password: upload it without one (a password is never kept)"` / `"The PDF cannot be opened: <reason>"` / `"The PDF has no pages"` / `"A document has at most 50 pages"` | `documents/store/DocumentBytes.java:43` – `:50`, `:86` – `:101`; `documents/store/MalwareScan.java:49` – `:53` |
| The scanner unreachable, slow, or answering neither clean nor infected; the GridFS store unreachable | **503** `"The malware scan could not be completed; nothing was stored"` / `"The document store cannot be reached; try again later"` | `documents/store/MalwareScan.java:54` – `:55`, `:73` – `:76`, `:90` – `:92`; `documents/store/DocumentBytes.java:104` – `:110` |
| Document absent, of another account, or of a held profile | 400 `"This account holds no such document"` (the same words for each) | `documents/DocumentService.java:54`, `:141` – `:149` |
| Confirming an attempt that is not the latest (or no `attemptId`); a `FAILED` reading; one already confirmed | 400 `"Only the latest reading of this document can be confirmed"` / `"A reading that failed has nothing to confirm"` / `"This reading is already confirmed"` | `documents/DocumentService.java:108` – `:115` |
| Accepting a row not read; a row both accepted and corrected; rejecting a row not read; correcting a path that is no single-value row of the layout; a path both accepted and rejected | 400 `"<path> was not read, so it cannot be accepted: correct it instead"` / `"<path> is both accepted and corrected"` / `"<path> was not read, so it cannot be rejected"` / `"<path> is not a row of FORM_130 that can be written"` / `"A field is accepted or rejected, not both: <path>"` | `documents/ConfirmedRows.java:28` – `:43`, `:47` – `:55`; `documents/ingest/DocumentConfirmation.java:24` – `:28` |
| A correction that does not read as its row's kind | 400 `"<path>: '<text>' is not an amount in rupees, e.g. 950000 or 950000.50"` / `"… is not yes or no"` / `"… is not a period as yyyy-MM-dd/yyyy-MM-dd"` | `documents/Corrections.java:29` – `:60` |
| Erasure of a profile absent, of another account, or already held | 400 `"This account holds no such profile"` (`TaxProfileService.owned`) | `profile/TaxProfileService.java:71` – `:72` |
| A job name no job has; the Gazette watch while `app.tax.gazette-watch.enabled` is false | 400 `"No tax job is named <name>; the jobs are [account-erasure, auto-archive, gazette-watch, log-retention, parse-documents, retention-purge, staleness-sweep]"` / `"The Gazette watch is off (app.tax.gazette-watch.enabled); runbook §1.w turns it on"` | `jobs/TaxJobDirectory.java:29` – `:34` |
| Cursor year before 2009 or after this year; `lastNumber` not positive | 400 `"The year must be a Gazette folder from 2009 to <this year>, but was <year>"` / `"The last number read must be positive, but was <n>"` | `gazette/GazetteWatchService.java:21`, `:42` – `:49` |
| A user's token on an admin route | 403 | `controller/GazetteWatchController.java:30`, `:37`; `controller/TaxJobController.java:22` |
| The confirmed employee PAN is another than the profile's, or is not among the confirmed rows | 400 `"This certificate is for another PAN than the profile's"` / `"The certificate's employee PAN (Part A item 11) is not read: correct it to confirm"` | `documents/DocumentService.java:118` – `:122` |

## 4. Stored data

PostgreSQL tables (W-337b, D-939/D-940 laid out 17 of the 20 as a dormant baseline; W-337f, D-951 built the entities,
added the 3 the baseline deferred, and replaced the GridFS bucket; `db/migration/taxplanning/V5__taxplanning.sql`
and `V10__taxplanning.sql`). Primary keys are caller-assigned `VARCHAR(255)`: 15 of the 20 entities implement
`CreatedWithId` (a caller sets the id and a transient `newlyCreated` flag before the first save, same idiom as
every other `CreatedWithId` entity app-wide); the other 5 (`ProfileErasure`, `GazetteLead`, `JobFailure`,
`JobLease`'s business key, `AccessRecord`) get a `@PrePersist`-assigned UUID if the caller leaves the id absent.
Every one of the 20 embeds `AuditColumns` (`created_by`, `updated_by`, `created_at`, `updated_at`) - 5 had no audit
trail at all under Mongo and gained it fresh in V10 (`tax_gazette_cursor`, `tax_gazette_leads`, `tax_access_log`,
`tax_job_failures`, `tax_job_leases`); `tax_gazette_cursor`'s pre-existing manual `updated_at`/`updated_by` columns
were folded into the same embeddable instead of being set by hand.

**Encrypted fields** (`EncryptedColumn` for text, same as every other module since W-337a) carry over field for
field from the Mongo-era `EncryptedField`, with one exception this port found: `FamilyMember.dateOfBirth` and
`TaxpayerProfile.dateOfBirth` (`LocalDate`) and both entities' `disability` (`DisabilitySeverity`) were encrypted
non-string fields - a shape no other module had yet. `EncryptedColumn` stays scoped to text; two new converters
cover it instead: `shared.protection.EncryptedDateColumn` (generic, any module) and
`taxplanning.profile.EncryptedDisabilitySeverityColumn` (lives in `taxplanning`, not `shared`, because `shared`'s
allowed dependencies are `portfolio` only and this converter must import a `taxengine` type). V5's baseline DDL
declared `date_of_birth` as a real `DATE` column, not knowing it would hold ciphertext; V10 corrects the column
type to `TEXT` on both tables.

**Opaque, never-queried-into fields** are `TEXT` JSON columns via `portfolio.entity.JsonColumnConverter` (reused
directly, not duplicated - `taxplanning`'s allowed dependencies permit `-> portfolio`): `DraftComputation.ruleChecksums`
(`Map<String,String>`, a new `compute.StringMapConverter`), `DraftRevision.importedFrom` (`WorkbookImport`) and
`FilingDraft.staleness` (`DraftStaleness`) - the latter two converters' `empty()` returns `null`, not a default
instance, since a column with no stored JSON means "never happened" here (a revision that didn't come from a
workbook import; a draft that isn't known stale), exactly the bug class `RealisedProfitsConverter` was fixed for in
`portfolio` (W-337e). **Plain lists of scalars** are `@ElementCollection` child tables instead, matching the shape
V5's baseline already chose for every other such field in this module (`GazetteLead.matchedTerms`/`.possiblyAffected`,
`RegimeElectionHistory.events`, `StoredConfirmation.rejectedFields`): `DraftComputation.lawAndExec` follows the same
pattern (`tax_draft_computation_law_and_exec`, `@OrderColumn` because `ComputationReportService` compares the list
with `equals`, which is order-sensitive).

**`tax_profiles`** (`profile/TaxpayerProfile.java`): `email` (`SearchableColumn`), `pan`/`name` (`EncryptedColumn`),
`pan_digest`, `kind`, `date_of_birth`/`disability` (as above), `residential_status`, `holds_portfolio`,
`date_of_death`, `erasure_requested_at`. Indexes: `email`; `pan_digest` unique (a bare `UNIQUE INDEX`, not a
partial one - Postgres already excludes `NULL` from a unique constraint, so a profile held for erasure, whose
digest is cleared, does not need a `WHERE` clause the way Mongo's sparse index needed one).

**`tax_family_members`**, **`tax_landlords`**, **`tax_lenders`**, **`tax_bank_accounts`**, **`tax_employers`**
(`profile/{FamilyMember,Landlord,Lender,BankAccount,Employer}.java`) - each `profile_id`-indexed (plain, except
`tax_employers`' unique `{profile_id, tan_digest}`), fields carried over field for field from the Mongo source with
the encrypted-field conversions above.

**`tax_profile_erasures`** (`profile/ProfileErasure.java`): `email` (`SearchableColumn`), `profile_id`,
`requested_at`, `erased_at`. No index, matching the Mongo source.

**`tax_year_workspaces`** (`workspace/TaxYearWorkspace.java`): `profile_id`, `tax_year_start`, `primary_draft_id`.
Unique `{profile_id, tax_year_start}`.

**`tax_filing_drafts`** (`workspace/FilingDraft.java`, new in V10): `workspace_id`, `profile_id`, `tax_year_start`,
`label`, `status`, `current_revision`, `inputs_json` (`EncryptedColumn`), `regime_election`, `acted_on_at`,
`archived_at`, `staleness` (opaque, above). Index `{workspace_id, status}`.

**`tax_draft_revisions`** (`workspace/DraftRevision.java`, new in V10): `draft_id`, `revision_number`,
`full_state_json`/`change_set_json` (`EncryptedColumn`), `changed_by` (`SearchableColumn`), `reopened`,
`imported_from` (opaque, above) **plus** `imported_from_workbook_sha256` - the one field of `WorkbookImport` pulled
out as its own column, because `DraftRevisionRepository.findFirstByDraftIdAndImportedFromWorkbookSha256` queries
into it; Spring Data JPA cannot derive a query path into a `@Convert`-mapped attribute, so the one field actually
queried is normalized out while the rest of the object stays opaque. Unique `{draft_id, revision_number}`.

**`tax_regime_election_history`** (`workspace/RegimeElectionHistory.java`) + child table
**`tax_regime_election_events`** (`history_id`, `tax_year_start`, `source_draft`): `profile_id`, `regime_code`,
`events` (`@ElementCollection`, `ElectionEvent` an `@Embeddable` record). Unique `{profile_id, regime_code}`.

**`tax_draft_computations`** (`compute/DraftComputation.java`, new in V10) + child table
**`tax_draft_computation_law_and_exec`** (`draft_computation_id`, `position`, `version`, ordered): `receipt`,
`email` (`SearchableColumn`), `draft_id`, `revision`, `result_json` (`EncryptedColumn`), `law_and_exec`
(`@ElementCollection`, ordered), `rule_checksums` (opaque, above), `computed_at`. Unique `receipt`; index
`draft_id`.

**`tax_documents`** (`documents/store/StoredDocument.java`): `profile_id`, `tax_year_start`, `document_type`,
`sha256`, `uploaded_at`, `page_count`, `scanned` - nothing encrypted, same as the Mongo source. Unique
`{profile_id, sha256}`; index `{profile_id, tax_year_start}`.

**`tax_parse_attempts`** (`documents/store/StoredParseAttempt.java`): `document_id`, `parser_id`, `parser_version`,
`started_at`, `finished_at`, `outcome`, `fields_json`/`unmapped_text_json`/`parser_notes_json` (`EncryptedColumn`).
Index `{document_id, started_at}`.

**`tax_document_confirmations`** (`documents/store/StoredConfirmation.java`) + child table
**`tax_document_confirmation_rejected_fields`** (`confirmation_id`, `rejected_field`): `confirmed_by`
(`SearchableColumn`), `confirmed_at`, `accepted_json` (`EncryptedColumn`), `rejected_fields`
(`@ElementCollection`). No index beyond the primary key.

**`tax_document_bytes`** (`documents/store/TaxDocumentBytes.java`, new in V10) replaces the GridFS bucket: one row
per document, `document_id` the primary key (caller-assigned, the document's own id, no auto-generation), `bytes`
(`BYTEA`, already sealed by `FieldCipher.encryptBytes` before it reaches this column - no column-level converter),
`uploaded_at`. Index on `uploaded_at` for the retention sweep's `storedBefore`. `DocumentBytes` (the service, not
the entity) is now a plain repository-backed class, not a hand-built `@Bean`; `DocumentRemoval.removeAll` deletes
the bytes row inline, in the same transaction as the document/attempt/confirmation deletes above it, instead of
the Mongo-era's post-commit deferral (GridFS "took no part in" the transaction; a plain table row does).

**`tax_access_log`** (`protection/AccessRecord.java`): `principal` (`SearchableColumn`), `subject`, `subject_id`,
`read_at` (`Instant`) - never a value. No index beyond the primary key.

**`tax_job_failures`** (`jobs/JobFailure.java`): `job`, `subject`, `reason`, `failed_at` - the dead letter, never a
value. No index.

**`tax_job_leases`** (`jobs/JobLease.java`): the primary key is literally `job` (the Mongo source's `@MongoId`
field was named `job`, not `id` - kept as-is), plus `holder`, `expires_at`. No TTL index - unlike Mongo, Postgres
has no expiring-document feature; an abandoned lease (a crashed run) is reclaimed lazily by the next acquire
attempt's own `expires_at < now` check instead, the same choice `auth`'s `refresh_tokens` made (D-937 C3a). The
acquire itself is one atomic `INSERT ... ON CONFLICT (job) DO UPDATE ... WHERE tax_job_leases.expires_at < :now`
(`jobs/JobLeaseRepository.java`), replacing Mongo's `findAndModify`-upsert-plus-`DuplicateKeyException` trick
(`jobs/TaxJobRunner.java`).

**`tax_gazette_cursor`** (`gazette/GazetteCursor.java`): the one row, id fixed to `GazetteCursor.ID` (`"egazette"`),
`year`, `last_number`. Since the id is always this fixed value (never absent, so never auto-generated), auditing
never sees this row as "new" - `created_by`/`created_at` stay null even on the row's first save, the one
consequence of a true singleton with a caller-assigned, unchanging id; `updated_by`/`updated_at` still fill
correctly on every save, matching the Mongo-era's manually-set behaviour.

**`tax_gazette_leads`** (`gazette/GazetteLead.java`) + child tables **`tax_gazette_lead_matched_terms`** and
**`tax_gazette_lead_possibly_affected`** (both `lead_id` + one text column): `url`, `year`, `number`, `page`,
`matched_terms`/`possibly_affected` (`@ElementCollection`), `note`, `found_at`. Unique `url`.

`@PostConstruct` index initializers (`TaxProfileIndexInitializer`, `WorkspaceIndexInitializer`,
`DraftComputationIndexInitializer`, `DocumentIndexInitializer`, `JobIndexInitializer`) are gone (W-337f) - every
index above is declared on the Flyway migration instead, matching every other already-ported module. The `*_json`
fields that are `ReadingJson`'s own shape (not opaque-converter JSON) are unaffected by any of this: each value
still names its kind (`MONEY` with an exact-text `amount`, `FLAG`, `PERIOD`, `TEXT`), and a kind it never wrote is
still refused (`documents/store/ReadingJson.java`).

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

### Documents (`documents/`)

- **Upload (`documents/DocumentService.java:69` – `:95`; `documents/store/DocumentBytes.java:41` – `:53`):** the switch, then the profile's owner (`TaxProfileService.owned`), then a parser for the form and a layout for the year. The SHA-256 of the bytes (`documents/parser/DocumentContent.java:23` – `:29`) is looked up under the profile first: the same bytes return the stored document (`DR 05` M7), and two uploads racing to store them meet the unique index, the loser answering the winner's document (`:87` – `:91`). New bytes are checked in this order, so the PDF library never reads an unscanned file: size, the ClamAV scan, the `%PDF-` start, then the page count read in a child process within `upload-read-budget`. Then they are sealed into `tax_document_bytes` under the document's own id (W-337f: a plain table row, not a GridFS bucket). `StoredDocument.scanned` records whether the scan was on (`:86`); `ParseRequests.request()` wakes the parse job (`:92`), which is part four's.
- **Malware scan (`documents/store/MalwareScan.java:14` – `:92`):** clamd's `zINSTREAM` over TCP to `scan.host:scan.port`, in 64 KiB chunks each led by its 4-byte length, ended by a zero length; it fails closed: only `stream: OK` passes, a reply ending ` FOUND` is the 400 (the signature name alone is logged), anything else is a 503. Off (`scan.enabled=false`), it passes every file without connecting, and `DocumentBytesConfig` logs `"The malware scan is off: uploads are stored unscanned (W-131h)"` once per start (`documents/store/DocumentBytesConfig.java:22` – `:25`).
- **Parsing (`documents/parser/`):** `DocumentParsers` holds one parser per form; two for one form fail the start (`documents/parser/DocumentParsers.java:29` – `:39`). It checks that the bytes are the document's, times the run in UTC and makes the `ParseAttempt` with a fresh id (`:44` – `:59`). The one parser is `Form130Parser`, id `form-130`, version `4` (`documents/parser/Form130Parser.java:23` – `:44`), reading against the year's layout from the pack (`backend/src/main/resources/rulepacks/timeline/forms/certificates/FORM_130.json`: 6 part headings, 3 Part B tables, 17 printed sums, 2 agreements, 7 comparisons). `CertificateReading` (`documents/parser/CertificateReading.java:50` – `:254`) never throws on a bad document; every failure is a `FAILED` reading with its reason (`:52` – `:68`): no layout in force, a password, an unreadable PDF, no text layer ("a scanned page is not read yet"), or no first part heading (`"Not a Form No. 130: no PART A"`, `:88` – `:91`). Otherwise:
  - **L1, labels (`documents/parser/LabelMatch.java:11` – `:174`):** each top part's lines are found in printed order by their label, a cursor only moving forward. A label's words may break across lines, with or without a hyphen; quotes and U+2010 hyphens are folded; a gap wider than 12 pt (`COLUMN_GAP`, `:20`) separates the label from its value cells. Words come from `PrintedText`: runs within 2 pt of a baseline are one line, within 0.8 pt one word (`documents/parser/PrintedText.java:26` – `:28`). A top part not printed is noted ("… is not in this certificate") and leaves the reading whole; a line or a group heading not found is noted and makes it `PARTIAL` (`documents/parser/CertificateReading.java:137` – `:147`, `:167` – `:172`).
  - **Tables (`documents/parser/TableReading.java:21` – `:350`; `documents/parser/HeadingMatch.java:10` – `:96`):** each Part B table by its title, then each column heading by its own words (up to 8 lines below the title, `TableReading.java:32`; a heading may wrap 3 lines, `HeadingMatch.java:19`), rows up to the Total row. A value goes to the column whose reach (halfway to the neighbouring headings) holds its centre, unless it reaches under another heading (`TableReading.java:267`). The untitled, one-row and captioned table shapes stay for a later form, each tested on its own, though no shipped layout uses them (D-749).
  - **L2, values (`documents/parser/CertificateReading.java:185` – `:211`; `documents/parser/CellReading.java:16` – `:72`):** a value cell is read by its line's kind: `AMOUNT` (numbers only, Indian or Western grouping or none, an optional leading minus, at most two decimals, exactly one per printed column, a multi-column line one field per column), `YES_NO`, `PERIOD` (`dd/mm/yyyy to dd/mm/yyyy`, strict days), anything else `TEXT`. A cell that does not read keeps its text with no value, is noted `"Not read: <path> (<text>)"` and makes the reading `PARTIAL` — never zero.
  - **Unmapped text (`documents/parser/CertificateReading.java:240` – `:254`):** words inside a printed part that no heading, label or value claims, line by line, other than the layout's own column headings, make the reading `PARTIAL`.
  - **L3, inference (`documents/parser/Inference.java:15` – `:88`):** where exactly one cell of a printed sum is unknown (nothing printed, or its line not found) and every other is read, that cell is solved, at confidence 0.80, noted `"Inferred <cell> from the sum printed for <target>"`, repeated until nothing more is determined. A cell printing text L2 could not read is never replaced.
  - **Contradictions (`documents/parser/Contradictions.java:22` – `:205`):** a sum whose cells are all read must hold exactly (`:90` – `:103`); a table's Total amount must equal its rows (`:109` – `:125`); a period must lie in the tax year with its first day not after its last (`:59` – `:70`); the standard deduction must fit row A's regime, else `"… Which regime did your employer apply?"` (`:142` – `:173`); the cess must be the year's rate × (tax + surcharge − rebate) within ₹10 (`:176` – `:194`). A contradiction is never resolved by picking a side: both figures are named and each cell drops to confidence 0.50, and any contradiction makes the reading `PARTIAL` (`documents/parser/CertificateReading.java:97` – `:100`). An agreement beyond its tolerance (Part B's total tax deducted against row 21 or Annexure-II's row 17, ₹10, `backend/src/main/resources/rulepacks/timeline/forms/certificates/FORM_130.json:1476` – `:1489`) is only shown as a `"Differs: …"` note (`Contradictions.java:76` – `:87`).
  - **Confidence:** 0.95 a label found with nothing to read, 0.90 a value read (`documents/parser/CertificateReading.java:37` – `:39`), 0.80 inferred, 0.50 contradicted (`documents/parser/Contradictions.java:33`).
- **Reading back and confirming (`documents/DocumentService.java:97` – `:139`; `documents/ConfirmedRows.java:24` – `:55`; `documents/Corrections.java:29` – `:60`):** the parse view shows the latest attempt by `started_at`, and the employee PAN only as whether it matches the profile's, trimmed and upper-cased (`documents/DocumentService.java:165` – `:174`; `documents/ParseView.java:10` – `:12`). A confirmation names the latest attempt; `accepted` rows are taken as read, `corrected` rows are read from text by the kind of the value read, or of the layout's line for a row the parser did not read; `rejected` rows must have been read. It is stored only when the confirmed employee PAN (the layout's `EMPLOYEE_PAN` fact) matches the profile's (B9-73). Nothing read is used until a confirmation exists (`documents/ingest/DocumentConfirmation.java:7` – `:10`).
- **Assembly (`documents/assembly/EmployerAssembly.java:18` – `:129`):** a year's confirmed certificates read together, each found by the facts its layout names, never by path. Mode (`:47` – `:49`): one Part C beside several certificates is `LAST_EMPLOYER_CONSOLIDATED`, a Part C on every certificate `PER_EMPLOYER`, anything else `MIXED`. Notices: a gap or an overlap between employers' periods (`ATTENTION`, `:76` – `:97`); a consolidating Part C whose row 1(e) is nil (`ACTION_REQUIRED`, `TDS-SAL-12B-001`, `:100` – `:108`); several employers' standard deductions above one year's ceiling for the regime row A states (`ACTION_REQUIRED`, `SAL-STDDED-001`/`-002`, `:114` – `:129`).
- **Reconciliation (`documents/reconcile/Reconciliation.java:19` – `:218`):** read by part two's `DraftReconciliationService`. A `MIXED` year answers `CANNOT_COMPARE` with `MIXED_YEAR`'s sentence and no lines (`:51` – `:53`). Otherwise each layout comparison (`backend/src/main/resources/rulepacks/timeline/forms/certificates/FORM_130.json:1490` – `:1525`) whose printed figure and computed figure both exist becomes a `ReconciliationLine` (`difference` = printed − computed). The printed figure is the one Part C covering the year (`:81` – `:93`), or, for Part B's table Total with several certificates, the sum over them (`:109` – `:113`). The computed figure is a total or a trace node's last output (`:147` – `:155`). Reasons (`:178` – `:199`): a summed Total short beyond ₹10 takes the employers' errors (`:120` – `:145`); else a comparison's own reason by direction (`whenHigher`/`whenLower`, `:170` – `:176`); a zero difference keeps a pairing reason (`TAXSUM`); ₹10 or less is `ROUNDING`; then `REGIME_DIFFERS`, `CAPITAL_GAINS`, or the own reasons of the lines above. Ten closed reasons, each `EXPECTED` or `EMPLOYER_ERROR` with its rule and sentence (`documents/reconcile/ReconciliationReason.java:11` – `:40`). Verdict (`documents/reconcile/Reconciliation.java:206` – `:214`): all within ₹10 `MATCHED`; every differing line explained by `EXPECTED` reasons `MINOR_DIFFERENCE`; else `MATERIAL_DIFFERENCE` (`documents/reconcile/Verdict.java:4` – `:12`). `explanations` carries each reason's sentence once, in line order (`documents/reconcile/Reconciliation.java:76` – `:78`).
- **Removal (`documents/store/DocumentRemoval.java`):** confirmations, then attempts, then documents, so a run cut off part-way leaves no confirmation without its attempt; the `tax_document_bytes` row goes last, inline in the same transaction (W-337f removed the Mongo-era's post-commit deferral — GridFS "took no part in" the transaction, a plain table row does). Profile erasure and retention (part four) both come through here.

### Isolation (`isolation/`, `documents/parser/`)

- **`IsolatedChild` (`isolation/IsolatedChild.java:16` – `:143`):** runs one `main` of the application's own jar (through Spring Boot's `PropertiesLauncher` inside the jar, `:63` – `:68`) as `/bin/sh -c "ulimit -t <cpu-seconds> && exec …"`, or, when the application runs as root, through `/bin/su … -- nobody` (`:50` – `:57`). The JVM gets `-Xmx<heap-mebibytes>m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:-UsePerfData`, every Java socket sent to a dead SOCKS proxy (`127.0.0.1:9`) and `jdk.net.hosts.file=/dev/null` (`:58` – `:62`). `run` (`:78` – `:113`) clears the environment (the database password and keys stay behind, `:84` – `:85`), feeds the input on one virtual thread and reads at most `max-reply-bytes` + 1 on another, killing a flood (`:116` – `:124`). A reply over the cap, a run past its budget or a non-zero exit is a `ChildReply.Failed` with its reason (`isolation/ChildReply.java:4` – `:21`), and the child is killed whatever happens (`isolation/IsolatedChild.java:111`). **Accepted gap (D-770):** the network is cut inside the JVM, not by the operating system (`:22` – `:23`). Part one's salary-workbook import uses the same class.
- **`IsolatedPdfRead` and `PdfReadChild` (`documents/parser/IsolatedPdfRead.java:13` – `:84`; `documents/parser/PdfReadChild.java:12` – `:51`):** `PdfReadChild` is the only code that hands untrusted bytes to iText. It has no Spring, rule pack or database, and replies one `PdfText(outcome READ / PASSWORD / UNREADABLE, pages, words, reason)` as JSON. The application parses that strictly (unknown, missing or null properties refused, `IsolatedPdfRead.java:24` – `:27`, `:73` – `:79`), and every child failure becomes an `UNREADABLE` reading with its reason (`:66` – `:71`, `:81` – `:84`). Page count at upload within `upload-read-budget`, words in the parse job within `parse-budget` (`:52` – `:60`).
- **`ProcessProtection` (`documents/parser/ProcessProtection.java:12` – `:61`):** before the first reader starts (`documents/parser/DocumentParsersConfig.java:20` – `:31`), the application marks itself non-dumpable on Linux through `prctl(PR_SET_DUMPABLE, 0)` and reads it back. A process of its own user, such as the reader, can then not read its `/proc/<pid>/environ`, `mem` or `maps`. Still dumpable after the call is a failed start; off Linux the call is skipped with a log line. The start log states how PDFs are isolated and that the operating system's network is not cut (`DocumentParsersConfig.java:25` – `:30`).

### Protection and erasure (`protection/`, `erasure/`)

- **`TaxAccessLog` (`protection/TaxAccessLog.java:10` – `:27`):** each read of special-category data appends one `AccessRecord` (who, which subject, when, never a value) inside the read's own transaction. Nothing updates one. Only the erasure of what it names (`AccessRecordRepository.deleteBySubjectIdIn`, `protection/AccessRecordRepository.java:8` – `:9`), the erasure of the reader's account, and the log retention job (a year after the read) delete it.
- **Erasure on request (`erasure/ErasureService.java:15` – `:40`):** `TaxProfileService.holdForErasure` stamps `erasure_requested_at` and clears `pan_digest`, so the PAN can be registered again at once (`profile/TaxProfileService.java:71` – `:78`). From then on the profile reads as absent everywhere. A `ProfileErasure` record is kept apart from the profile, so a restore from an older snapshot cannot undo the request. The receipt names `eraseOn` = request + `app.privacy.erasure-hold`, the five kinds of data erased, why they are held until then, and the privacy contact.
- **Whole-account erasure (`erasure/TaxAccountErasure.java:22` – `:77`):** `TaxAccountErasure` is the tax module's `PersonalDataHolder` (notice part `tax-planning`). `hold` holds every active profile of the account; `erase` removes each profile through `ProfileRemoval`, then what is kept by email: draft computations, the person's own reads, and their profile-erasure records. `reapply` (after a restore) removes again each profile erased since the snapshot, and holds again each still waiting.

### Jobs (`jobs/`)

- **Framework:**
  - `TaxJob` is a name, a lease timeout and `run(JobContext)`; every job is idempotent, so a run cut off is completed by the next, and no retry policy is kept (`jobs/TaxJob.java:5` – `:17`).
  - `TaxJobRunner.run` takes the lease with one atomic statement (`jobs/JobLeaseRepository.java`, W-337f): `INSERT ... ON CONFLICT (job) DO UPDATE ... WHERE tax_job_leases.expires_at < :now`, so a live lease's row simply doesn't update and the acquire reports zero rows affected — replacing the Mongo-era's `findAndModify` upsert plus `DuplicateKeyException`-on-collision trick. It runs the job as the job's own name for auditing, and removes the lease it holds when done (`jobs/TaxJobRunner.java`). A failure inside a job is not caught: it reaches the caller, and the lease lapses at its timeout.
  - `JobContext.holdsLease` checks the run's own holder against an unexpired lease before each change (`jobs/TaxJobRunner.java:60` – `:63`), so a run overtaken while slow stops. `refuse` writes a `tax_job_failures` row and logs at ERROR (`jobs/JobContext.java:30` – `:45`).
  - **When jobs run:** `TaxJobSchedule` exists only with `app.tax.jobs.enabled=true` (off in all three yamls, W-336). It schedules six jobs by cron, the Gazette watch only while its own switch is on, and runs the staleness sweep at every start (`jobs/TaxJobSchedule.java:13` – `:74`). On request, `TaxJobDirectory` runs any job by name through the same runner and lease (`jobs/TaxJobDirectory.java:10` – `:37`). `ParseRequests` starts a parse run after an upload only where the schedule exists (`jobs/ParseRequests.java:7` – `:20`).
- **`auto-archive` (`jobs/AutoArchive.java:17` – `:63`, lease 30 min):** archives each `DRAFT` whose `audit_metadata.updated_at` is before now − `archive-after` (in UTC, as auditing writes it since W-402, D-959). It skips its workspace's primary draft and any draft of a profile held for erasure.
- **`retention-purge` (`jobs/RetentionPurge.java:22` – `:114`, lease 1 h), in order:**
  1. Erases each profile held for at least `erasure-hold`: its `ProfileErasure` is marked erased first, then `ProfileRemoval` runs (`:87` – `:102`).
  2. Deletes archived drafts never acted on whose `archived_at` is before now − `purge-archived-after`.
  3. Deletes drafts ever acted on once today is after the end of tax year (start year + `TaxCalculator.longestCarryForward(year)`), the span read from the rule pack. A year with no such span is refused and kept (`:67` – `:80`).
  4. Runs `DocumentRetention`.
  5. Runs `ThirdPartyRetention`.

  Each delete checks the lease first.
- **`DraftRemoval` (`jobs/DraftRemoval.java:12` – `:35`):** in one transaction, a draft's computations, then its revisions; it unsets the draft as its workspace's primary, then deletes the draft. The access log and the regime election history stay.
- **`ProfileRemoval` (`jobs/ProfileRemoval.java`):** in one transaction: every draft (through `DraftRemoval`), then the access records naming the profile, any of its drafts, any of its receipts or any of its documents. Then its documents with what was read from them (`DocumentRemoval`, the bytes row now inline, not after commit). Then every `profile_id`-scoped record — family members, employers, landlords, lenders, bank accounts, workspaces and regime election history — each via its own repository's `deleteByProfileId` (W-337f replaced a single raw `MongoTemplate.remove` loop over all seven with these). Last, the profile itself.
- **`DocumentRetention` (`jobs/DocumentRetention.java`):** for each distinct `tax_year` of `tax_documents`, deletes the year's documents once the same span as filed drafts has ended; a year with no span is refused and kept. Then deletes `tax_document_bytes` rows older than `orphan-after` that no document names, left by an upload race or a removal cut off part-way.
- **`ThirdPartyRetention` (`jobs/ThirdPartyRetention.java:22` – `:100`):** a landlord or lender is deleted once today is after the end of its last supported tax year + `third-party-after`. That year is the later of the latest year of a draft naming it and the year it was last entered or changed (`audit_metadata` updated, else created). A record with neither date is refused and kept.
- **`parse-documents` (`jobs/ParseDocuments.java:32` – `:132`, lease 10 min):**
  - A document is due while every attempt at it failed and none was by the current parser version (`:73` – `:84`); a profile held for erasure is not read.
  - Bytes that cannot be read are refused (a failure row). Bytes whose SHA-256 is not the document's become a `FAILED` attempt.
  - Otherwise the parse runs on a daemon thread within `parse-budget`. Past it the thread is interrupted and abandoned, and the attempt is `FAILED` with `"The parser took longer than PT30S"`; a parser that throws is `FAILED` with `"The parser stopped: <exception class>"` (`:102` – `:120`). The PDF itself is read in part three's child process.
- **`account-erasure` (`jobs/AccountErasure.java:12` – `:45`, lease 1 h):** runs `shared.privacy.AccountErasureService` over accounts whose purpose has lapsed, then those whose hold has passed. The work is the shared module's; it runs here for the lease and the dead letter.
- **`log-retention` (`jobs/LogErasure.java:17` – `:49`, lease 1 h):** `shared.privacy.LogRetention` erases the app-wide logs. This job then deletes `tax_access_log` rows with `read_at` at or before `LogRetention.cutoff`, and `ProfileErasure` records erased by then. Each step is a delete by date.
- **`staleness-sweep` (`jobs/StalenessSweep.java:24` – `:111`, lease 30 min):** for each `READY` or `ACTED_ON` draft of a profile not held, takes its latest computation (by revision, then `computed_at` with none first, then receipt, `:39` – `:41`). If the law and exec versions it ran under (`law_and_exec`, or for an older one the copy inside `result_json`, `:104` – `:111`) differ from the pack's current versions, it sets `FilingDraft.staleness` with the rules whose checksum changed or which the pack no longer holds. It never recomputes, and a draft already flagged against the same current versions is left alone. A year with no shipped pack is refused.

### Gazette watch (`gazette/`)

- **`gazette-watch` (`gazette/GazetteWatch.java:15` – `:130`, lease 60 min):** with no cursor it refuses ("no cursor: seed it with PUT /api/v1/admin/tax/gazette-cursor"). Otherwise it reads the numbers after the cursor, one request every `pause`, up to `max-numbers-per-run`, while it holds the lease (`:62` – `:79`).
  - A copy found is matched, recorded as a lead if it is one (a second insert of the same URL is ignored), and the cursor moves to it.
  - After `misses-to-stop` missing numbers in a row it stops, unless the clock's year is later than the folder's. Then it tries the next year's folder once, continuing the numbers after the cursor, since numbers run on across years (`:68` – `:76`).
  - A copy larger than `max-document-size` is a lead on page 0, noted "too large to read … open it".
  - The watch never writes a pack.
- **Fetching (`gazette/GazetteCopies.java:8` – `:34`; `gazette/GazetteWatchConfig.java:9` – `:23`):** `GET <base-url>/<year>/<number>.pdf` with the watch's own User-Agent and a connect and read timeout. 404 is absence; any other non-2xx throws, so the run fails with the cursor still before that number. The body is read to at most one byte past the size limit.
- **Matching (`gazette/GazetteMatcher.java:20` – `:118`):** the copy is read page by page with iText's text extractor, whitespace collapsed and typeset hyphens closed ("Income -tax" → "Income-tax", `:96` – `:98`). The first page by which the copy has named any `lead-when-any` term, or all `lead-when-all` terms, makes it a lead (`:54` – `:76`). That page and the next two are then searched for "section N of the Income-tax Act, 1961 / 2025", and each shipped rule citing that section (`TaxCalculator.provisionsCited`) is listed as `possibly_affected`, for triage only (`:30` – `:33`, `:78` – `:89`).

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
6. **Inference, L3 (`documents/parser/Inference.java:52` – `:75`):**
   - Form No. 130 prints row 3 = 1(d) − 2(f) (`backend/src/main/resources/rulepacks/timeline/forms/certificates/FORM_130.json:884` – `:897`).
   - Read: 1(d) `5,00,000` and 2(f) `50,000`; row 3 printed nothing. Balance over the known cells: 0 − (+1 × 5,00,000) − (−1 × 50,000) = −4,50,000; the target is the unknown cell, so row 3 = −balance = **`4,50,000`**, confidence 0.80, no text, the note `"Inferred partC.annexureI.row3 from the sum printed for partC.annexureI.row3"` (matches `InferenceTest.java:50` – `:60`).
   - Read: 1(d) `5,00,000` and row 3 `4,50,000`; 2(f) blank. Balance = 4,50,000 − 5,00,000 = −50,000; 2(f)'s sign is −1, so 2(f) = −1 × −50,000 = **`50,000`** (`InferenceTest.java:64` – `:69`).
7. **L2 amounts (`documents/parser/CellReading.java:24` – `:25`, `:34` – `:47`):** `12,00,000.00` matches the Indian grouping (`12`, `,00`, `,000`, `.00`) → **1200000.00**; `1,200,000` the Western → **1200000**; `12,0000` matches neither (after `12,00` a group of three is needed, and `12,000` leaves a `0`) → not read, its text kept, `PARTIAL`.
8. **Reconciliation, T8-1 (`ReconciliationGoldenTest.java:125` – `:144`):**
   - Two certificates, A (1-4-2026 – 30-9-2026) and B (1-10-2026 – 31-3-2027), each with a Part C, 1(e) nil, 4(b) `75,000`, Part B's total tax deducted `0`, row A "No".
   - Mode: 2 Part Cs on 2 certificates → `PER_EMPLOYER` (`documents/assembly/EmployerAssembly.java:47` – `:49`). Standard deductions 75,000 + 75,000 = 1,50,000, above one year's `75,000` (the test's hand working, 21,00,000 − 75,000 = 20,25,000) → `STANDARD_DEDUCTION_TWICE`. B, the later by period, prints 1(e) nil → `PREVIOUS_SALARY_UNDECLARED` (`documents/reconcile/Reconciliation.java:120` – `:145`).
   - Part B's Total line, summed over both: 0 + 0 = 0, against the bill `2,14,500` → difference **`−2,14,500`**, beyond ₹10, so it takes both employer errors (`:178` – `:181`) → **`MATERIAL_DIFFERENCE`** (`:206` – `:214`), as the test asserts.
9. **Reconciliation, T8-2 (`ReconciliationGoldenTest.java:147` – `:170`):**
   - One certificate under the old regime; row 11 (Deductible Amount) prints `1,50,000`; the computation's Chapter VIII is `60,000`; row 12 prints `10,00,000` against the computed total income `10,90,000`.
   - Row 11: +90,000, beyond ₹10 and higher → `whenHigher` = `DECLARED_NOT_PROVEN` (`backend/src/main/resources/rulepacks/timeline/forms/certificates/FORM_130.json:1498` – `:1502`), an `EXPECTED` reason.
   - Row 12: −90,000, no reason of its own. Row A "Yes" leaves the year's default regime, as computed, so the regime does not differ; no capital gains → it takes the reason above, `DECLARED_NOT_PROVEN` (`documents/reconcile/Reconciliation.java:178` – `:199`).
   - Every differing line explained by `EXPECTED` reasons → **`MINOR_DIFFERENCE`**, as the test asserts.
10. **Gazette watch, one run (`gazette/GazetteWatch.java:58` – `:79`; `misses-to-stop` 20):**
    - Cursor `2026/277961`, clock in 2026. The run asks for 277962: found, so it is matched and the cursor moves to `2026/277962`. Then 277963 – 277982 are 20 misses in a row; the clock's year (2026) is not later than the folder's, so it stops with the cursor at `2026/277962`.
    - The same, clock in 2027. After the 20th miss it rolls once: year `2027`, number reset to the cursor's `277962` and then advanced, so it asks for `2027/277963` next. A further 20 misses stop it, since it has rolled already. The cursor moves to the 2027 folder only when a copy there is found.

The retention and erasure dates are worked by hand in §11 (W-137, W-235).

## 6. Configuration

Properties read by the module (shipped identically across `application.yaml`, `application-staging.yaml` and `application-prod.yaml` unless noted):

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
| `app.tax.documents.enabled` | `false` in all three yamls: the three documents routes and part two's reconciliation answer 503 | `application.yaml:148` – `:149`; `application-staging.yaml:123` – `:124`; `application-prod.yaml:123` – `:124` |
| `app.tax.documents.max-bytes` / `max-pages` | `20971520` (20 MiB) / `50` | `application.yaml:141` – `:143`; `application-staging.yaml:116` – `:118`; `application-prod.yaml:116` – `:118`; `documents/store/DocumentSettings.java:19` – `:21` |
| `app.tax.documents.orphan-after` / `parse-budget` | `P1D` / `PT30S` (read by part four's jobs and by the words read) | `application.yaml:144` – `:147`; `application-staging.yaml:119` – `:122`; `application-prod.yaml:119` – `:122` |
| `app.tax.documents.scan.*` (`enabled` / `host` / `port` / `timeout`) | `true` in `application.yaml`, **`false` in staging and prod** (D-769) / `${CLAMD_HOST:localhost}` / `3310` / `PT60S` | `application.yaml:157` – `:164`; `application-staging.yaml:132` – `:139`; `application-prod.yaml:132` – `:139`; `documents/store/DocumentSettings.java:30` |
| `app.tax.jobs.enabled` | `false` in all three yamls (W-336): nothing is scheduled, and the owner runs a job through `POST /api/v1/admin/tax/jobs/{name}/run` | `application.yaml:106` – `:109`; `application-staging.yaml:81` – `:84`; `application-prod.yaml:81` – `:84` |
| `app.tax.jobs.*-cron` | `auto-archive` `0 0 3 * * SUN`; `retention-purge` `0 0 4 1 * *`; `parse-documents` `0 * * * * *`; `account-erasure` `0 30 4 * * *`; `log-retention` `0 45 4 * * *`; `gazette-watch` `0 0 5 * * *` (the staleness sweep runs at start) | `application.yaml:110` – `:118`; `application-staging.yaml:85` – `:93`; `application-prod.yaml:85` – `:93` |
| `app.tax.gazette-watch.*` | `enabled` `false`; `base-url` `https://egazette.gov.in/WriteReadData`; `user-agent` `WealthLens-GazetteWatch/1.0`; `timeout` `10s`; `pause` `1s`; `max-numbers-per-run` `400`; `misses-to-stop` `20`; `max-document-size` `40MB`; `lead-when-any` `["central board of direct taxes"]`; `lead-when-all` `["income-tax act", "department of revenue"]` | `application.yaml:119` – `:131`; `application-staging.yaml:94` – `:106`; `application-prod.yaml:94` – `:106`; `gazette/GazetteWatchSettings.java:23` – `:26` |
| `app.tax.retention.*` | `archive-after` `P18M`; `purge-archived-after` `P12M`; `third-party-after` `P2Y` | `application.yaml:135` – `:139`; `application-staging.yaml:110` – `:114`; `application-prod.yaml:110` – `:114`; `jobs/RetentionPeriods.java:13` – `:14` |
| `app.privacy.erasure-hold` (the shared module's, read here) | `P1Y` | `application.yaml:73`; `application-staging.yaml:48`; `application-prod.yaml:48` |
| Job leases (Java constants) | `auto-archive` 30 min, `retention-purge` 1 h, `parse-documents` 10 min, `account-erasure` 1 h, `log-retention` 1 h, `staleness-sweep` 30 min, `gazette-watch` 60 min | `jobs/AutoArchive.java:37` – `:39`; `jobs/RetentionPurge.java:53` – `:55`; `jobs/ParseDocuments.java:58` – `:60`; `jobs/AccountErasure.java:30` – `:32`; `jobs/LogErasure.java:36` – `:38`; `jobs/StalenessSweep.java:56` – `:58`; `gazette/GazetteWatch.java:45` – `:47` |
| Parser constants | label 0.95, read 0.90, inferred 0.80, contradicted 0.50; `COLUMN_GAP` 12 pt; reconciliation `ROUNDING` ₹10; `Form130Parser` id `form-130`, version `4` | `documents/parser/CertificateReading.java:37` – `:39`; `documents/parser/Inference.java:23`; `documents/parser/Contradictions.java:33`; `documents/parser/LabelMatch.java:20`; `documents/reconcile/Reconciliation.java:29`; `documents/parser/Form130Parser.java:29` – `:36` |

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
- `TaxProfileIntegrationTest` (13): profile and all five reference CRUD routes over HTTP + PostgreSQL (W-337f), ownership isolation, PAN/TAN masking, unique PAN/TAN digests, `holdsPortfolio` uniqueness, access log writes.
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

### Part three (0 plan or review citations)

**Unit tests** (`backend/src/test/java/com/thiru/wealthlens/taxplanning/documents/`; counts are methods annotated `@Test` or `@ParameterizedTest`):
- `ingest/` (4 classes, 28): `DocumentConfirmationTest` (4), `ExtractedFieldTest` (10), `IngestedDocumentTest` (5), `ParseAttemptTest` (9): each value refusing an impossible state.
- `parser/` (11 classes, 107): `CellReadingTest` (8), `ContradictionsTest` (20), `DocumentContentTest` (3), `DocumentParsersTest` (4), `Form130ParserTest` (24, PDFs rendered by `CertificateFixture` / `TableFixture`), `HeadingMatchTest` (5), `InferenceTest` (8), `IsolatedPdfReadTest` (12: real child reads, and hanging, flooding, failing, garbage, spinning, network and environment children), `LabelMatchTest` (1), `ProcessProtectionTest` (2, the Linux case skipped elsewhere), `TableReadingTest` (14).
- `assembly/EmployerAssemblyTest` (8), `reconcile/ReconciliationTest` (15), `reconcile/ReconciliationGoldenTest` (5: `DR 08` T8-1 – T8-4 and T8-6 against real engine runs).
- `store/` (3 classes, 21): `DocumentBytesTest` (7), `MalwareScanTest` (9), `ReadingJsonTest` (5).

**Integration tests** (`backend/src/test/java/com/thiru/wealthlens/integration/`):
- `DocumentIntegrationTest` (11): upload once and again for the same bytes, a year or form refused, malware found, a scanner closing without answering (503), another account's profile and document, waiting then read, confirmation with a corrected row, another person's PAN, a correction that does not read, erasure removing the readings.
- `DocumentScanOffIntegrationTest` (1): the scan off stores the upload unscanned without calling the scanner.

### Part four (2 plan citations re-homed by W-357b10d; `plans/W-137.md`, `plans/W-235.md` deleted)

**Unit tests** (`backend/src/test/java/com/thiru/wealthlens/taxplanning/`):
- `jobs/TaxJobDirectoryTest` (5): a job run by name through the runner, a held lease, an unknown name, the Gazette watch off and on.
- `gazette/GazetteMatcherTest` (5): the Finance Act's page naming the Board, the Rules' English notification, a CBDT amendment of section 10, a Revenue notice naming only a Finance Act and a health-ministry notification.
- `protection/PanLogMaskTest` (3): the shipped log configuration writes a PAN masked, in messages and stack traces.

**Integration tests** (`backend/src/test/java/com/thiru/wealthlens/integration/`):
- `TaxJobsIntegrationTest` (21; cites §11 W-137): auto-archive, the retention purge of archived, acted-on and pack-less drafts, documents and orphan bytes, leases, third-party retention and the staleness sweep.
- `TaxErasureIntegrationTest` (5; cites §11 W-235): the receipt and hold, the PAN registered again, the purge erasing everything under the profile and nothing else, auto-archive leaving a held profile alone.
- `ParseDocumentsIntegrationTest` (6), `GazetteWatchIntegrationTest` (6), `GazetteWatchEndpointIntegrationTest` (3), `TaxJobEndpointIntegrationTest` (3).
- Whole-account erasure is exercised through the shared module's `AccountErasureIntegrationTest` (8) and `ErasureReapplicationIntegrationTest` (9).

## 8. Known gaps (W-395 – W-398)

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

**Documents and isolation (W-397):**
16. **`DocumentController.java:23`'s Javadoc** still cites `UserValidator` and "the path's email", both gone since W-358a / W-358c1 (grep finds no `UserValidator` class), as gap 5 found on two profile controllers.
17. **One certificate without a Part C is read as a `MIXED` year** (`documents/assembly/EmployerAssembly.java:47` – `:49`: 0 Part Cs is neither 1 among several nor every certificate). Reconciliation then answers `CANNOT_COMPARE` with `"Some employers issued Part C and some did not, …"` (`documents/reconcile/Reconciliation.java:32` – `:33`, `:51` – `:53`), a sentence about several employers, for one. `EmployerAssemblyTest`'s only `MIXED` case has three certificates (`:116` – `:126`).
18. **`ConfirmationRequest` carries no Bean Validation constraints** though `DocumentController.java:44` annotates it `@Valid`. A body without `attemptId` answers `"Only the latest reading of this document can be confirmed"` (`documents/DocumentService.java:108` – `:109`) rather than naming the missing field.
19. **"No parser reads FORM_16 yet"** (`documents/DocumentService.java:73` – `:74`): `DocumentType` keeps `FORM_16`, `FORM_122` and `FORM_123` (`documents/ingest/DocumentType.java:7` – `:19`), none with a parser. "yet" promises a Form 16 parser that D-748 / D-749 ruled out.
20. **Inline fully qualified names:** `java.util.Map` / `java.util.Set` (`documents/DocumentService.java:117`, `:124`), `java.util.function.Function` (`documents/assembly/EmployerAssembly.java:61`), `java.time.format.DateTimeFormatter` declared after the methods that use it (`documents/parser/Contradictions.java:127`), `java.util.Comparator` / `java.util.stream.Collectors` (`:153`, `:159`), `java.util.TreeMap` (`documents/parser/CertificateReading.java:243`), where every other class imports.

**Protection, erasure, jobs and the Gazette watch (W-398):**
21. **`ErasureController.java:16`'s Javadoc** still cites `UserValidator` and "the path's email" (gaps 5 and 16 again).
22. **`tax_access_log` has no index** (`protection/AccessRecord.java:20`). It is deleted by `subject_id` (`jobs/ProfileRemoval.java:66`), by `principal` (`erasure/TaxAccountErasure.java:55`) and by `read_at` (`jobs/LogErasure.java:44` – `:45`), each a full scan of a collection that grows with every read.
23. **One copy that is not a readable PDF stops the Gazette watch for good.** `GazetteMatcher.open` throws on it (`gazette/GazetteMatcher.java:104` – `:109`), and the run fails with the cursor still before that number (`gazette/GazetteWatch.java:63` – `:66`), so every later run fails on the same number. Only a non-2xx answer is tested (`GazetteWatchIntegrationTest`, `run_whenACopyCannotBeFetched_failsWithTheCursorBeforeIt`).
24. **Gazette copies are read by iText inside the application's own process** (`gazette/GazetteMatcher.java:54` – `:66`), where an upload's PDF is read only in part three's isolated child. The bytes come from a remote site.
25. **`GazetteCursorRequest` carries no Bean Validation constraints** though `GazetteWatchController.java:38` annotates it `@Valid`; the checks are in `GazetteWatchService.seed` (`:42` – `:49`).
26. **No `api-collection` request** for `GET /api/v1/admin/tax/gazette-leads` or `PUT /api/v1/admin/tax/gazette-cursor` (grep finds "gazette" only in `Tax Admin/Run a tax job.yml`).
27. **Stale Javadoc:** `jobs/StalenessSweep.java:36` – `:37` says "the audit's creation time is never filled for a computation (W-329)", as gap 15 found in `compute/DraftComputation.java`; `CreatedWithId` now marks it new.

## 9. Not covered here

- **Whole-account erasure, the privacy notice, log retention and `AccountErasureService`:** `docs/modules/shared.md`; this module holds only `TaxAccountErasure`, the tax part of it.
- **Tax rules, nodes, inputs, rule packs, filing forms and optimiser:** `docs/modules/taxengine.md`.
- **Portfolio lots, trade outcomes and capital-gains summary:** `docs/modules/portfolio.md`.
- **Zones, `@CallerEmail`, `ProblemDetail`, `EncryptedField` / `SearchableIdentifier`, whole-account erasure:** `docs/modules/shared.md`.
- **Sign-in, tokens and roles:** `docs/modules/auth.md`.

## 11. Hand-worked figures the tests cite

Moved verbatim from the plans cited by `taxplanning`'s tests (W-357b10b, C1a; same convention as `docs/modules/taxengine.md` §11). `W-089`'s T13-1 working already lives in `docs/modules/taxengine.md` §11 (W-089), where `DraftCalendarServiceTest`, `DraftForecastServiceTest` and `DraftComputationIntegrationTest` now point. `plans/W-088g9.md` had no other non-history citation and was deleted (readable at `1f5fce4b`); `plans/W-088g.md`, `plans/W-088g2.md` and `plans/W-089.md` stay while other kept plans (`W-088g1/g2/g4/g5`, `W-112b`) still cite them. W-357b10d moved `W-137`'s and `W-235`'s hand-worked tests here and deleted both plans (readable at `d8ef2aad`).

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

### W-137 — background jobs, auto-archive and retention purge

Moved verbatim from `plans/W-137.md` "Tests, by hand (as of a fixed `Clock`)" (W-357b10d, C1a); `TaxJobsIntegrationTest` cites it.

- **Archive:** at 2026-11-06, 18 months back is 2025-05-06.
  - A `DRAFT` last changed 2025-05-05 → archived; one changed 2025-05-07 → kept.
  - The primary, or a `READY` draft, both changed 2025-05-05 → kept.
- **Purge, archived:** at 2026-11-06, 12 months back is 2025-11-06. `archived_at` 2025-11-05 → deleted with its
  revisions and computations; 2025-11-07 → kept.
- **Purge, acted on, TY 2025-26:** the year ends 31-3-2026, and its 8 succeeding years end 31-3-2034.
  - At 2034-03-31 → kept; at 2034-04-01 → deleted.
  - The same, archived by the user 2026-05-01, at 2027-06-01 → kept (12 months passed, 8 years not).
- **No pack:** an `ACTED_ON` draft for TY 2019-20 → kept, one failure row naming 2019-20.
- **Lease:** a second runner while a lease is live → does nothing; after expiry → runs. A run on stale data
  (fencing token moved on) → stops before deleting.
- **Engine:** `longestCarryForward` for TY 2025-26 and 2026-27 → 8, read from the shipped packs; a fixture pack
  without the rows → empty.
- **Probes:** drop the `acted_on_at` check → the archived-after-acting case is deleted, so its test fails; bypass the
  lease → the second-runner test fails.

### W-235 — erasure on request

Moved verbatim from `plans/W-235.md` "Tests, by hand" (W-357b10d, C1a); `TaxErasureIntegrationTest` cites it.

- Request 2026-11-07T10:00Z, hold P1Y → `eraseOn` 2027-11-07T10:00Z. Purge at 2027-11-07T09:59Z → kept; at 10:00Z →
  every row with that `profile_id` gone, in all ten collections, and its access records; another profile on the same
  account untouched.
- After the request: `GET` profile → 400 "This account holds no such profile"; compute → the same; registering the
  same PAN on a new profile → 201 (digest freed). A second request → 400 (already held, not reset).
- Purge run twice → the second changes nothing.
- **Probes:** purge ignores the hold → the 09:59Z case fails; `pan_digest` kept → the re-register case fails (409/400).
