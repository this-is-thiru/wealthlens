# Module `shared` — the privacy duties, and what every module stands on

> W-357b8 (D-914), written from the code at `review/taxplanning` on 2026-11-22. Every claim cites the line it was read from; a bare
> path is under `backend/src/main/java/com/thiru/wealthlens/shared/`. Template: `docs/taxplanning-build/plans/W-357b.md` C2a.

## 1. Purpose

`shared` holds two different things:
- **The privacy duties** the Digital Personal Data Protection Act, 2023 and its Rules, 2025 put on the app: the notice and consent,
  grievances, breach notices, whole-account erasure, the processing log and its retention (`privacy/`, 47 files). This is the only part
  with routes or stored data.
- **What every module stands on:**
  - the route zones and `@CallerEmail` (`web/`);
  - the one error shape (`exception/`);
  - field encryption (`protection/`);
  - audit metadata and the dynamic query filter (`entity/`);
  - transaction and Mongo configuration (`config/`);
  - the `T`-prefixed utilities (`util/`).

## 2. Boundaries

- `OPEN` module, `allowedDependencies = {"portfolio"}`
  (`backend/src/main/java/com/thiru/wealthlens/shared/package-info.java:1` – `:5`). The reverse dependency exists because shared types
  name portfolio's: the query filter checks fields against `AssetEntity.ALLOWED_FIELDS` (`entity/query/QueryFilter.java:58`, `:68` –
  `:69`).
- **Every module depends on it.**
- **It declares what other modules implement**, since the reverse would be a cycle:
  - `PersonalDataHolder` (`privacy/PersonalDataHolder.java:7` – `:13`), implemented by `auth`'s `LoginErasure`, `portfolio`'s
    `PortfolioRecordErasure`, `insurance`'s `InsuranceRecordErasure`, `taxplanning`'s `TaxAccountErasure` and its own
    `PrivacyRecordErasure` (grep);
  - `RegisteredUsers` (`privacy/RegisteredUsers.java:6` – `:22`), implemented in `auth`.
- `taxplanning`'s account-erasure job runs `AccountErasureService.lapsed` and `.due` (`taxplanning/jobs/AccountErasure.java:36` – `:37`).
- URL segment `privacy` (`web/ApiZones.java:26` – `:27`).

## 3. Routes

| Method and path | Zone | Body → answer | Where |
|---|---|---|---|
| `GET /api/v1/public/privacy/rights` | public | → `PrivacyNotice` | `controller/PrivacyController.java:18` – `:21` |
| `GET /privacy` | open (`AuthConfig`'s public paths) | → the notice as an HTML page, from the same settings | `controller/PrivacyPageController.java:22` – `:26`; `privacy/PrivacyNotice.java:18` |
| `POST /api/v1/me/privacy/erasure` | me | → 202 `AccountErasureReceipt`; holds the account at once | `controller/AccountErasureController.java:29` – `:33` |
| `GET /api/v1/me/privacy/erasure` | me | → the latest receipt | `AccountErasureController.java:35` – `:38` |
| `POST /api/v1/me/privacy/grievances` | me | `GrievanceStatement` (`text`) → 201 `GrievanceReceipt` with the date it will be answered by | `controller/GrievanceController.java:31` – `:35` |
| `GET /api/v1/me/privacy/grievances` | me | → the caller's grievances, newest first | `GrievanceController.java:37` – `:40` |
| `GET /api/v1/me/privacy/breach-notices` | me | → the caller's breach notices, newest first; the first read is recorded | `controller/UserBreachNoticeController.java:20` – `:23` |
| `POST /api/v1/admin/privacy/erasure/reapply` | admin | `ErasureReapplication` (`snapshotTakenAt`) → `ReappliedErasures` | `controller/AccountErasureAdminController.java:27` – `:31` |
| `GET /api/v1/admin/privacy/erasure/users/{email}` | admin | → that user's receipt | `AccountErasureAdminController.java:34` – `:38` |
| `GET /api/v1/admin/privacy/grievances` | admin | → unanswered grievances, soonest due first | `controller/GrievanceQueueController.java:27` – `:31` |
| `POST /api/v1/admin/privacy/grievances/{grievanceId}/answer` | admin | `GrievanceStatement` → `GrievanceReceipt` | `GrievanceQueueController.java:33` – `:37` |
| `POST /api/v1/admin/privacy/breach-notices` | admin | `BreachNoticeRequest` → 201 `BreachCase` | `controller/BreachNoticeController.java:34` – `:39` |
| `POST …/breach-notices/{noticeId}/board-intimated`, `…/board-report-sent` | admin | → `BreachCase`, the step's time recorded once | `BreachNoticeController.java:41` – `:51` |
| `POST …/breach-notices/{noticeId}/board-extension` | admin | `BoardExtension` (`reportDueAt`, `reference`) → `BreachCase` | `BreachNoticeController.java:53` – `:57` |
| `POST …/breach-notices/{noticeId}/emailed` | admin | `BreachEmails` → `BreachReport` | `BreachNoticeController.java:59` – `:63` |
| `GET …/breach-notices/{noticeId}/report` | admin | → `BreachReport`: the case and each affected user's posted, read and emailed times | `BreachNoticeController.java:65` – `:69` |

Every admin route carries `@PreAuthorize("hasRole('SUPER_USER')")` too (`AccountErasureAdminController.java:28`, `:34`;
`GrievanceQueueController.java:28`, `:34`; `BreachNoticeController.java:35`, `:42`, `:48`, `:54`, `:60`, `:66`).

**Refusals (400):**

| When | Message | Where |
|---|---|---|
| A receipt asked for an email with no request | "No erasure was requested for …" | `privacy/AccountErasureService.java:54` – `:57` |
| Reapply with no snapshot time, or one in the future | "Give the restored snapshot's time, snapshotTakenAt"; "The snapshot's time … is in the future" | `AccountErasureService.java:96` – `:100` |
| A grievance or answer with no text; an unknown grievance; one answered already | "The text of a grievance is required" / "… an answer …"; "No grievance …"; "Grievance … was answered at …" | `privacy/GrievanceService.java:50` – `:68` |
| A breach notice missing any of its four texts | "A breach notice states the breach, its consequences, the mitigation and the safety measures a user may take (DPDP Rules r.7(1)(a) – (d))" | `privacy/BreachNoticeService.java:142` – `:149` |
| No awareness time, or one in the future; no scope; nobody affected | "When the breach became known is required, and cannot be in the future"; "Who is affected is required: LISTED or EVERY_REGISTERED_USER"; "A breach notice must reach at least one affected user" | `BreachNoticeService.java:38` – `:40`, `:129` – `:140` |
| A Board step recorded twice; an extension not later, or without a reference; emailed users none, or not affected; an unknown notice | "The Board was first told of breach … at …", …, "Not affected by breach …: …", "No breach notice …" | `BreachNoticeService.java:59` – `:111`, `:163` – `:165` |
| Registration against a notice version not the published one (`auth` calls it) | "Read the privacy notice at GET /privacy (or GET /api/v1/public/privacy/rights) and register with its version" | `privacy/PrivacyConsents.java:27` – `:32` |

## 4. Stored data

PostgreSQL tables (W-337c2, D-943; Flyway `db/migration/shared/V3__privacy.sql:4` – `:98`). Each table's `email`-like column is encrypted and searchable (`SearchableColumn`); free text is encrypted (`EncryptedColumn`). Primary keys are caller-assigned `VARCHAR(255)` with `@PrePersist` UUID default.

| Table | Columns | Where |
|---|---|---|
| `privacy_erasure_requests` | `email` (indexed), `requested_at`, `cause` (`REQUESTED`, `PURPOSE_LAPSED`; absent reads as requested), `erased_at`, `created_by`/`updated_by`/`created_at`/`updated_at` (`AuditColumns`); kept after the erasure as its record | `privacy/AccountErasureRequest.java:25` – `:66`; `privacy/ErasureCause.java:4` – `:11` |
| `privacy_grievances` | `email` (indexed), `text` and `answer` (encrypted), `received_at`, `respond_by` (the date promised at receipt), `answered_at`, `AuditColumns` | `privacy/Grievance.java:24` – `:66` |
| `privacy_breach_notices` + `privacy_breach_notice_affected` | `description`, `consequences`, `mitigation`, `safety_measures`, `became_aware_at`, `scope`, `affected` (eager `@ElementCollection` in `privacy_breach_notice_affected`, `notice_id` FK), `posted_at`, `board_report_due_at`, `board_extension_reference`, `board_first_intimated_at`, `board_report_sent_at`, `AuditColumns` | `privacy/BreachNotice.java:29` – `:90` |
| `privacy_breach_intimations` | `notice_id` and `email` (indexed), `posted_at`, `read_at`, `emailed_at`, `AuditColumns` | `privacy/BreachIntimation.java:22` – `:60` |
| `privacy_consents` | `email` (unique `privacy_consents_email_idx`), `notice_version`, `given_at` (consent at registration), `noticed_at` (an older account first shown the notice), `AuditColumns` | `privacy/PrivacyConsent.java:24` – `:57` |
| `privacy_processing_log` | `principal` (indexed) and `subject_email`, `method`, `route` (the pattern, never the path), `status`, `remote_address` (encrypted), `recorded_at` (indexed), `request_id`; append-only, no audit columns | `privacy/ProcessingRecord.java:22` – `:72` |

Schema and indexes are managed by Flyway (`V3__privacy.sql:4` – `:98`).

## 5. Behaviour

**The notice** (`privacy/PrivacySettings.java:30` – `:98`) is built from `app.privacy.*`. It holds:
- the rights, each with its section and means;
- the identifier and the contact;
- the grievance period and the purpose lapse;
- the notice before consent: the personal data, item by item for each `PersonalDataHolder`'s `noticePart()`, then the purpose, the
  withdrawal and the Board complaint.

Its **version is the SHA-256 of that wording**, so no change of wording keeps an old version (`:81` – `:97`). A start with any part
missing, or a purpose lapse not longer than the erasure hold, is refused (`:34` – `:78`; `ConsentNotice.java:17` – `:33`).

**Consent** (`PrivacyConsents.java:27` – `:68`):
- **Registration** must send back the published version; it is recorded as `given_at`.
- **An account registered before W-250** is recorded as `noticed_at` at its first sign-in, once.

**Grievances** (`GrievanceService.java:29` – `:75`):
- Each is promised an answer `response-days` from receipt (30). The promise is stored, so a later change of period breaks none.
- The owner's queue is unanswered grievances, soonest due first.

**A breach** (`BreachNoticeService.java:35` – `:190`):
- **Posting** resolves the affected users, listed or every registered user, and gives each an intimation.
- **The Board's report** is due `board-report-hours` (72) after awareness, or later if the Board allowed it.
- **Each Board step** is recorded once.
- **A user's first read** of their notices is recorded. The report lists every user's posted, read and emailed times.

**Whole-account erasure** (`AccountErasureService.java:33` – `:123`; `HeldAccounts.java:11` – `:20`):
- **A request holds the account at once.** Sign-in, every token and the AMC billing ask `HeldAccounts`, and each holder's `hold` runs. A
  second request returns the same receipt.
- **The erasure runs** once `erasure-hold` (P1Y) has passed. The job calls `erase` on every holder in order, the login last
  (`PersonalDataHolder.java:43` – `:47`), then stamps `erased_at`.
- **A purpose lapse:** an account not signed in to for `purpose-lapse` (P3Y) gets a `PURPOSE_LAPSED` request and is erased in the same
  run, with no hold. An account with no sign-in recorded starts counting from the first run.
- **Reapply after a restore:** every account erased at or after the snapshot is erased again, every hold asked then is held again, and
  each module re-applies its own part-erasures. A second call changes nothing.
- **`shared`'s own part** (`PrivacyRecordErasure.java:27` – `:34`):
  - **Deleted:** the person's grievances, intimations, consent and their own requests in the processing log.
  - **Kept:** the breach notice itself, with their email pulled from its `affected`.

**The processing log** (`privacy/ProcessingLogFilter.java:27` – `:96`). A row is written after every request that names a person:
the signed-in user, or the email a sign-in was attempted for. It records:
- whose data was reached: the path's `{email}`, else the principal;
- the route pattern, the status and the remote address;
- the time and the request id.

A request with no person writes nothing.

**Retention** (`privacy/LogRetention.java:28` – `:47`). Once `log-retention` (P1Y) has passed since each row's last processing, it
deletes:
- log rows;
- answered grievances;
- intimations whose posted, read and emailed times are all past;
- completed erasure requests.

Unanswered grievances and breach notices stay.

**Routes and the caller** (`web/`):
- **Prefixes:** `ApiZones` gives every controller `/api/v1/{public|me|admin}/{module}`, from its annotation and its package
  (`ApiZones.java:19` – `:51`).
- **The caller:** `@CallerEmail` resolves to the token's name and refuses a request without one (`CallerEmailResolver.java:14` – `:29`).
- **The request id:** `RequestIdFilter` runs first of all filters. It gives each request a fresh UUID, which the answer returns as
  `X-Request-Id` and every log line prints (`RequestIdFilter.java:23` – `:48`).
- **The API spec:** `ApiDocs` keeps `@CallerEmail` out of it and shows a page as `page` and `size` (`ApiDocs.java:13` – `:24`).

**Errors** (`exception/ControllerAdviser.java:36` – `:243`): every error is an RFC 9457 `ProblemDetail`.
- **400:** `IllegalArgumentException` (and `BadRequestException`); an unreadable body (named field and what it accepts); a missing
  parameter or part; a wrong type; an upload too large; a constraint (each field named).
- **401** bad credentials; **403** access denied; **404** no resource; **405** wrong method; **415** wrong media type.
- **429** `TooManyAttemptsException`, with `Retry-After`; **503** `ServiceUnavailableException`; **409** `TradeCollisionException`.
- **500** anything else, answered "Something went wrong. Quote X-Request-Id … when you report it" and logged with its stack trace.
- A refusal is logged at WARN with its message only.

**Field protection** (`protection/`):
- **The cipher:** AES-256-GCM, the stored form prefixed with its key id `k1:` (`FieldCipher.java:21` – `:118`):
  - a fresh nonce per value for `EncryptedField` (text, a date or an enum, `EncryptedField.java:15` – `:46`);
  - a nonce that is the value's keyed digest for `SearchableIdentifier`, so equal values store equally and stay queryable
    (`SearchableIdentifier.java:18` – `:45`).
- **Digests:** `IdentifierDigest` is an HMAC-SHA256 for unique indexes (`IdentifierDigest.java:14` – `:33`).
- **Keys:** both must be 32 bytes in base64, or the start is refused (`KeyBytes.java:13` – `:22`).

**Dynamic queries** (`entity/query/QueryFilter.java:49` – `:83`; `QueryBuilder.java:22` – `:65`):
- **The caller's own email is forced:** a filter naming another is dropped, and the caller's added.
- **Only `AssetEntity.ALLOWED_FIELDS`** may be filtered: `email`, `transaction_date`, `transaction_type`, `account_type`,
  `account_holder`, `exchange_name`, `stock_code`, `broker_name`, `asset_type` (`portfolio/entity/AssetEntity.java:155` – `:156`).
- **Encrypted fields:** `account_holder` is matched exactly only.

**Expressions stored as data** run only in a read-only SpEL context; a type, constructor or bean reference is refused when written
(`util/expression/SafeExpressions.java:37` – `:72`).

**Transactions:** `app.mongodb.transactions-enabled`, true when absent, creates the `MongoTransactionManager`. Without it,
`@Transactional` does nothing (`config/MongoDbTransactionConfig.java:18` – `:26`). `TransactionSafetyAuditor` logs which at start
(`config/TransactionSafetyAuditor.java:14` – `:35`).

**Utilities** (`util/`):
- `TMoney`: paise canonicalisation, decision TL-8 (`docs/modules/portfolio.md` §10).
- `FinancialYear`: `2025-2026` labels and parsing.
- `XirrCalculator`: Newton, up to 100 iterations to 1e-7, then bisection (`util/calculator/XirrCalculator.java:10` – `:12`).
- `DoubleUtil`'s tolerance.
- The Excel writers and parser.
- `TJsonMapper`, `TCollectionUtil`, `TOptional` and `TStringUtil`.

### Worked examples (by hand)

**A grievance's promise.** Raised at 2026-11-22T10:00:00 UTC with `response-days` 30: `respond_by` is 2026-12-22T10:00:00. A later change of
`response-days` to 20 leaves that date as it is, and a new grievance raised then is promised 20 days.

**A breach's Board deadline.** Awareness stated as 2026-11-20T09:30:00 UTC with `board-report-hours` 72: due 2026-11-23T09:30:00. An
extension to 2026-11-25T09:30:00 with the Board's reference replaces it. One to 2026-11-23T09:00:00, not later, is refused.

**An erasure's timeline.** Requested 2026-11-22T10:00:00 UTC (`REQUESTED`):
- the account cannot sign in from that moment;
- `erasedFrom` is 2027-11-22T10:00:00, the request plus P1Y;
- the first job run at or after then erases it.

**A purpose lapse.** An account last signed in 2023-11-20T08:00:00 is past P3Y on 2026-11-20T08:00:00. A job run on 2026-11-22 opens its
request with `requested_at` 2026-11-20T08:00:00, the moment the purpose lapsed, and erases it in the same run.

## 6. Configuration

| Property | Shipped (`application.yaml`) | Effect |
|---|---|---|
| `app.privacy.contact` | `${PRIVACY_CONTACT}` (`application.yaml:42`) | named in every rights response; a start without it is refused |
| `app.privacy.identifier`, `rights` | `application.yaml:43` – `:61` | the notice's rights, each with its section and means |
| `app.privacy.grievance.response-days`, `longest-days-allowed` | 30, 90 (`application.yaml:64`, `:66`) | the promise; a promise over the limit stops the start (`privacy/GrievancePeriod.java:11` – `:22`) |
| `app.privacy.breach.board-report-hours` | 72 (`application.yaml:70`) | the Board's deadline; at least 1 (`privacy/BreachReporting.java:10` – `:17`) |
| `app.privacy.erasure-hold`, `log-retention`, `purpose-lapse` | P1Y, P1Y, P3Y (`application.yaml:73`, `:76`, `:80`) | the hold before erasure; how long logs and privacy records are kept; when an unused account lapses (the owner's ruling, D-480) |
| `app.privacy.consent.*` | `application.yaml:83` – `:101` | the notice before consent: the personal data by part, the purpose, the withdrawal, the Board complaint |
| `app.personal-data.data-key`, `digest-key` | `${TAX_DATA_KEY}`, `${TAX_DIGEST_KEY}` | the cipher's and the digest's keys (`protection/PersonalDataKeys.java:9` – `:11`) |
| `app.mongodb.transactions-enabled` | true | multi-document transactions |

## 7. Tests

| Class | Cases |
|---|---|
| `ControllerAdviserTest` | 28 |
| `PrivacySettingsTest` | 26 |
| `FieldCipherTest` | 13 |
| `ProcessingLogIntegrationTest` | 12 |
| `PurposeLapseIntegrationTest`, `ErasureReapplicationIntegrationTest`, `BreachNoticeIntegrationTest` | 11, 10, 10 |
| `GrievanceIntegrationTest`, `AccountErasureIntegrationTest` | 9 each (the latter shows the email pulled from a breach notice, `AccountErasureIntegrationTest.java:316`) |
| `ConsentNoticeIntegrationTest`, `RouteAccessIntegrationTest`, `FinancialYearTest` | 8 each |
| `XirrCalculatorTest` | 7 |
| others | `CallerMistakeIntegrationTest` (the error shapes), `EmailLogMaskTest`, `PanLogMaskTest`, `RequestIdIntegrationTest` |

Counts are `@Test` / `@ParameterizedTest` annotations per file (grep), not executions.

## 8. Known gaps (placed as W-388, D-914)

1. **The published privacy notice states a false fact about passwords.** The notice before consent says the sign-in keeps "your password
   (kept only as a one-way BCrypt hash)" in all three profiles (`application.yaml:87`; `application-staging.yaml:62`;
   `application-prod.yaml:62`). Passwords are written as Argon2id since W-358d3, and BCrypt is only read for older hashes
   (`config/InvestmentTrackerConfig.java:16` – `:25`). Correcting it changes the notice's version (§5), so new registrations meet the
   corrected wording. The wording is the owner's.
2. **A query filter that omits `isDateField` answers 500.** `QueryBuilder.sanitize` unboxes the `Boolean` (`QueryBuilder.java:60` – `:65`;
   `QueryFilter.java:30`).
   - **Shown at the build** (a throwaway integration test, deleted): `POST /api/v1/me/portfolio/assets/search` with
     `{filterKey: stock_code, operation: EQUALS, value: TCS}` answered 500.
3. **`STARTS_WITH` is neither a prefix nor literal.** It passes the caller's text to `regex` unanchored and unescaped
   (`QueryBuilder.java:43`).
   - **Shown at the build:** `STARTS_WITH "CS"` found TCS.
   - **Risk:** a caller's pattern runs on the server, so a costly one is the caller's to choose.
4. **Framework and JDK text reach the caller.** These pass `ex.getMessage()` through (`ControllerAdviser.java:40` – `:44`, `:79` – `:89`,
   `:99` – `:103`, `:125` – `:129`, `:143` – `:147`):
   - every `IllegalArgumentException`, whoever threw it (e.g. `Enum.valueOf`'s "No enum constant …");
   - 401, 403, 404, 405 and 415.

   A constraint failure with no field error would make `fields` empty, and `charAt(0)` would fail as a 500 (`:109` – `:113`).
5. **Request types with no constraint**, so their `@Valid` checks nothing: `GrievanceStatement`, `BreachNoticeRequest`, `BreachEmails`,
   `BoardExtension`, `ErasureReapplication`. Their checks live in the services.
6. **Unpaged lists:** `GET /me/privacy/grievances`, `/admin/privacy/grievances` and `/me/privacy/breach-notices` answer whole lists
   (`GrievanceController.java:37` – `:40`; `GrievanceQueueController.java:27` – `:31`; `UserBreachNoticeController.java:20` – `:23`).
   `noticesFor` also reads each notice one by one (`BreachNoticeService.java:124`).
7. **`TransactionSafetyAuditor` catches `Exception`**, an index out of bounds used as control flow (`TransactionSafetyAuditor.java:24` – `:31`).
   Its log text writes `@{Transactional}`.
8. **Untrue Javadoc:** `PrivacyPageController` says it is a view "so the JSON wrapper leaves it alone" (`PrivacyPageController.java:13` –
   `:14`). There has been no wrapper since W-359a.
9. **Twelve routes have no request in `api-collection/`** (grep at `41a7123`): the grievance routes, the breach-notice routes, and the
   two admin erasure routes.

## 9. Not covered here

- Sign-in, tokens and the login's erasure: `docs/modules/auth.md`.
- Each module's own erasure part: its module doc (`portfolio`, `insurance`; `taxplanning`, W-357b10).
- The jobs that run erasure and retention, and their switch: `taxplanning` (W-357b10); deploy runbook §5.2.
