# Module `taxplanning` — profiles, drafts, computations, documents and tax operations

> Written from the code at `review/taxplanning`; every claim cites the line it was read from, and a bare path is under
> `backend/src/main/java/com/thiru/wealthlens/taxplanning/`. Template: `docs/taxplanning-build/plans/W-357b.md` C2a. The module is
> written in four parts (D-923): boundary, profiles, workspaces, drafts and salary import (W-357b10a, D-924); assembly, portfolio
> bridge, computation, advisory views and report (**W-357b10b**, to come); documents (**W-357b10c**, to come); protection, erasure,
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

## 3. Routes

Every route in part one is in the `me` zone (`@MeApi`, prefix `/api/v1/me/tax`), taking the authenticated user from `@CallerEmail`.

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

### Refusals (part one)

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

## 4. Stored data

Part one covers the 11 `@Document` collections in `profile/` (7) and `workspace/` (4). Every entity implements `AuditableEntity` (`audit_metadata`), and 10 of the 11 implement `CreatedWithId` (`newlyCreated` transient flag so Spring Data Mongo treats a caller-assigned UUID as an insert; `ProfileErasure` does not).

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

`TaxProfileIndexInitializer` (`profile/TaxProfileIndexInitializer.java:20` – `:35`) creates the indexes for the first six profile collections at `@PostConstruct`, and `WorkspaceIndexInitializer` (`workspace/WorkspaceIndexInitializer.java:16` – `:31`) creates the indexes for all four workspace collections; neither catches errors, so a failure stops startup.

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

## 6. Configuration

Properties read by part one (shipped identically across `application.yaml`, `application-staging.yaml` and `application-prod.yaml` unless noted):

| Property / constant | Shipped value | Where |
|---|---|---|
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `21MB` / `22MB` | `backend/src/main/resources/application.yaml:9` – `:11` |
| `spring.jackson.deserialization.fail-on-unknown-properties` | `true` | `backend/src/main/resources/application.yaml:18` – `:22` |
| `spring.data.web.pageable.*` (`serialization-mode` / `default-page-size` / `max-page-size`) | `via-dto` / `50` / `200` (applies to `GET …/drafts/{draftId}/revisions`) | `backend/src/main/resources/application.yaml:25` – `:29` |
| `app.tax.documents.isolation.*` (`heap-mebibytes` / `cpu-seconds` / `upload-read-budget` / `max-reply-bytes`) | `256` / `30` / `PT10S` / `16777216` (shared by `SalaryImportConfig`) | `application.yaml:151` – `:156`; `application-staging.yaml:126` – `:131`; `application-prod.yaml:126` – `:131`; `workspace/importing/SalaryImportConfig.java:13` – `:15` |
| `SalaryImportService.MAX_BYTES` | `1_048_576` (1 MiB, Java constant) | `workspace/importing/SalaryImportService.java:49` |
| `SalaryStructureWorkbook.MAX_ROWS` | `200` (Java constant) | `workspace/importing/SalaryStructureWorkbook.java:34` |
| `DraftSaveRetry.ON_COLLISION` | `maxRetries=5`, `delay=20ms`, `multiplier=2`, `maxDelay=500ms`, `jitter=10ms` | `workspace/DraftSaveRetry.java:33` – `:40` |

## 7. Tests

Part one's tests (0 plan or review citations — nothing to re-home):

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

## 8. Known gaps (W-395)

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

## 9. Not covered here

- **Parts two to four of `taxplanning`** (to be added in `W-357b10b` – `d`): `EngineInputsAssembler`, `portfolio/` capital-gains adapter, `compute/` routes and PDF report, `documents/` and `isolation/`, `protection/`, `erasure/`, `jobs/`, `gazette/` and admin routes.
- **Tax rules, nodes, inputs, rule packs, filing forms and optimiser:** `docs/modules/taxengine.md`.
- **Portfolio lots, trade outcomes and capital-gains summary:** `docs/modules/portfolio.md`.
- **Zones, `@CallerEmail`, `ProblemDetail`, `EncryptedField` / `SearchableIdentifier`, whole-account erasure:** `docs/modules/shared.md`.
- **Sign-in, tokens and roles:** `docs/modules/auth.md`.
