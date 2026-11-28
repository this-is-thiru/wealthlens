# Module `auth` — accounts, sign-in, tokens

> W-357b4 (D-905), written from the code at `review/taxplanning` on 2026-11-22. Every claim cites the line it was read from. Template:
> `docs/taxplanning-build/plans/W-357b.md` (tag `docs-history-m3`) C2a.

## 1. Purpose

Who a caller is and what they may do. The module registers an account, checks a password at sign-in, issues a 15-minute access token and a
14-day rotating refresh token, limits guessing, changes a password and erases a login. It also holds the security rules every route goes
through: three zones, and deny by default. Everything after a sign-in — the token check on each request — is Spring Security's resource
server, configured here.

## 2. Boundaries

- `OPEN` module, `allowedDependencies = {"shared"}` (`backend/src/main/java/com/thiru/wealthlens/auth/package-info.java:1` – `:5`).
- **From `shared` it uses:**
  - `ApiZones` (`config/AuthConfig.java:4`);
  - `ProcessingLogFilter` (`:3`);
  - `HeldAccounts`, `PrivacyConsents` and `PrivacyNotice` (`service/AuthService.java:10` – `:12`);
  - the password encoder and the clock (`shared/config/InvestmentTrackerConfig.java:22` – `:25`, `:35`);
  - `PersonalDataHolder` and `RegisteredUsers` (`service/LoginErasure.java:4`, `service/RegisteredUserDirectory.java:9`).
- **What other modules depend on:** `shared` lists `auth` nowhere. `portfolio` and `taxplanning` list it (CLAUDE.md's table), and
  `SecurityAuditorAware` is the auditor every module's `audit_metadata` gets its actor from.
- URL segment `auth` (`shared/web/ApiZones.java:26` – `:27`).

## 3. Routes

**The security rules** (`config/AuthConfig.java:51` – `:71`):
- **Open:** `/api/v1/public/**`, `/privacy` and `/error` (`:42`, `:57`).
- **Signed-in:** `/api/v1/me/**` (`:59`).
- **`SUPER_USER`:** `/api/v1/admin/**` and `/actuator/**` (`:61`).
- **Everything else is denied** (`:64`).
- **Sessions:** stateless (`:65`), and CSRF is off (`:54`).
- **Tokens:** a public path ignores any `Authorization` header, so a stale token never blocks a sign-in or a refresh (`:73` – `:78`).
- **The processing log** records a request before its token is checked (`:69` – `:70`).

| Method and path | Zone | Body → answer | Where |
|---|---|---|---|
| `POST /api/v1/public/auth/register` | public | `RegistrationRequest` (email, password, `privacyNoticeVersion`) → text `user with username <email> added to system ` | `controller/AuthController.java:71` – `:74`; `service/AuthService.java:43` – `:63` |
| `POST /api/v1/public/auth/login` | public | `LoginRequest` (email, password) → `LoginResponse` | `AuthController.java:35` – `:56` |
| `POST /api/v1/public/auth/token/refresh` | public | `RefreshRequest` (`refresh_token`) → `RefreshResponse` | `AuthController.java:58` – `:62` |
| `POST /api/v1/public/auth/logout` | public | `RefreshRequest` → 204 | `AuthController.java:64` – `:69` |
| `PUT /api/v1/me/auth/password` | me | `RegistrationRequest` (email, password, `newPassword`) → text `<email>'s password changed successfully` | `controller/PasswordController.java:15` – `:24`; `AuthService.java:66` – `:89` |

**`LoginResponse`** (`dto/LoginResponse.java:10` – `:20`):
- `access_token` and `expires_in` (900);
- `refresh_token`;
- `tokenType` `Bearer`;
- `privacy_notice` and `privacy_notice_version`.

**`RefreshResponse`** (`dto/RefreshResponse.java:6` – `:13`): `access_token`, `expires_in`, `refresh_token` and `tokenType`.

**Refusals:**

| When | Status, message | Where |
|---|---|---|
| A wrong password, an unknown address, or a held account | 401 `Bad credentials`, the same for each, so the answer names no account (measured, D-884) | `service/UserDetailsServiceImpl.java:22` – `:29` |
| 5 failed sign-ins for one address from one client within 15 minutes | 429 with `Retry-After` | `service/SignInAttempts.java:43` – `:51`; `application.yaml:34` – `:37` |
| 30 calls from one client to `/api/v1/public/auth/**` within 15 minutes | 429 with `Retry-After` | `SignInAttempts.java:31` – `:40`; `config/SignInLimitInterceptor.java:25` – `:33` |
| A notice version that is not current, at registration | 400, checked first | `AuthService.java:47` |
| A taken or held address, at registration | 400 `This email cannot be registered`, the same for both | `AuthService.java:49` – `:51` |
| A refresh token that is unknown, spent, ended or expired | 401 `The refresh token is not valid; sign in again`, the same for each | `service/RefreshTokens.java:58` – `:74`, `:99` – `:101` |
| No refresh token | 400 `refresh_token is required` | `dto/RefreshRequest.java:8` – `:12` |
| Change password: the body's email is not the caller's / the new password equals the old / the old one is wrong | 400 `Email mismatch` / `Old password cannot be same as new password` / `Invalid old password` | `AuthService.java:68` – `:84` |

## 4. Stored data

PostgreSQL tables (W-337c1, D-941; Flyway `db/migration/V1__auth_and_insurance.sql`):

**`user_details`** (`entity/UserDetail.java:20` – `:50`), one login per row:

| Column | Stored as | Where |
|---|---|---|
| `id` | `VARCHAR(255)` primary key, caller-assigned (UUID defaulted in `@PrePersist` when absent) | `UserDetail.java:25` – `:27`, `:45` – `:50` |
| `email` | encrypted, searchable (`SearchableColumn`); unique index `user_details_email_idx` | `:29` – `:32`; `V1__auth_and_insurance.sql:12` |
| `password` | a labelled hash: `{argon2}…` from W-358d3, or unlabelled BCrypt from before, rewritten at its owner's next sign-in | `:34` – `:35`; `InvestmentTrackerConfig.java:22` – `:25`; `AuthConfig.java:84` – `:85` |
| `roles` | comma-separated role names, `USER` at registration; a higher role is granted by the owner in the database (runbook §0.1) | `:37` – `:38`; `AuthService.java:56` – `:57` |
| `last_signed_in_at` | `TIMESTAMP(6)` UTC, the last sign-in or the registration (W-249) | `:43` – `:44` |

**`refresh_tokens`** (`entity/RefreshToken.java:25` – `:66`) + child table **`refresh_token_factors`**, one row per refresh token issued:

| Column | Stored as | Where |
|---|---|---|
| `token_hash` | SHA-256 of the token, unique index `refresh_tokens_token_hash_idx`; the token itself is never stored | `:34` – `:35`; `RefreshTokens.java:108` – `:114` |
| `family_id` | indexed (`refresh_tokens_family_idx`); shared by every token rotated from one sign-in | `:37` – `:38` |
| `email` | encrypted, searchable (`SearchableColumn`), indexed (`refresh_tokens_email_idx`) | `:40` – `:42` |
| `factors` | eager `@ElementCollection` in `refresh_token_factors` (`refresh_token_id` FK → `refresh_tokens.id`) | `:45` – `:48` |
| `issued_at`, `used_at`, `revoked_at` | `TIMESTAMPTZ(6)` instants | `:50` – `:59` |
| `expires_at` | `TIMESTAMPTZ(6)`, indexed (`refresh_tokens_expiry_idx`); swept on each sign-in by `RefreshTokens.start` (D-937 C3a) | `:53` – `:54`; `RefreshTokens.java:43` – `:48` |

Schema and indexes are managed by Flyway (`V1__auth_and_insurance.sql:5` – `:33`).

## 5. Behaviour

- **Registration** (`AuthService.java:43` – `:63`), in order:
  1. The notice version must be current.
  2. The address must be neither taken nor held.
  3. The password is hashed with Argon2id and the role set to `USER`.
  4. `last_signed_in_at` is set to now, and the consent is recorded.
- **Sign-in** (`AuthController.java:35` – `:56`):
  1. The attempt is named in the processing log.
  2. The failure limit is checked, then the password is checked by Spring's `DaoAuthenticationProvider` (`AuthConfig.java:80` – `:87`).
  3. A failure is counted. A success clears the count, then records the sign-in and the notice shown (`AuthService.java:95` – `:100`).
  4. It issues the two tokens (`:106` – `:113`).
  5. If the stored hash was older than Argon2id, Spring hands the new hash to `updatePassword`. That stores it by a targeted update by email,
     never a save (`UserDetailsServiceImpl.java:36` – `:42`, `RegisteredUserDirectory.java:45`; W-369).
- **Access token** (`service/AccessTokens.java:22` – `:32`; `config/AccessTokenConfig.java:43` – `:82`):
  - **Contents:** a JWT, HS256 on `JWT_SECURITY_KEY`, its key id the key's RFC 7638 thumbprint. The subject is the email, it expires
    15 minutes after issue, and the `authorities` claim holds the roles as `ROLE_…` plus the factors.
  - **Checks on every request:** the signature, the expiry on the app's clock, and that the account is not held. A held account's token stops
    at once (`:74` – `:78`).
- **Refresh** (`AuthService.java:119` – `:130`, `RefreshTokens.java:58` – `:79`):
  1. The presented token is spent exactly once: a conditional `findAndModify` holds even under two concurrent calls (`:61` – `:62`).
  2. A token presented after it was spent ends its whole family (`:63` – `:68`).
  3. The roles are read again, so a role change holds from the next refresh. A held or erased account ends the family.
  4. The successor token keeps the family, the factors and the original expiry.
- **Logout** ends the presented token's family, answering the same for an unknown token (`RefreshTokens.java:82` – `:84`).
- **A password change** ends every sign-in of the account (`AuthService.java:85` – `:87`, `RefreshTokens.java:91` – `:93`).
- **Erasure** runs last of all the holders (`LoginErasure.java:27` – `:30`). It deletes the refresh tokens, then the login (`:22` – `:25`).
- **Guessing limits** are kept in memory, per instance, so a restart clears them (`SignInAttempts.java:16` – `:20`). The address is
  lower-cased and trimmed (`:80` – `:82`), and the client is the connection's own address (`SignInLimitInterceptor.java:26`).
- **Auditing:** a row's `audit_metadata` actor is the `runAs` actor of a job, else the signed-in caller, else `unknown`
  (`config/SecurityAuditorAware.java:21` – `:48`).

### Worked example (by hand)

A refresh token is 32 random bytes in URL-safe Base64 with no padding (`RefreshTokens.java:40` – `:41`, `:108` – `:110`):
⌈32 × 8 / 6⌉ = ⌈42.67⌉ = **43 characters**. W-366b and W-366c row 4 saw exactly 43 (run log, D-896, D-897).

## 6. Configuration

| Key | Shipped value | Where |
|---|---|---|
| `app.security.key-secret` | `${JWT_SECURITY_KEY}`, 32 bytes in Base64, required | `application.yaml:38` – `:39`; `config/SecurityConfig.java:5` – `:8` |
| `app.auth.sign-in-limit.failures-per-address`, `.calls-per-client`, `.window` | 5, 30, 15m; each must be positive | `application.yaml:34` – `:37`; `config/SignInLimit.java:11` – `:18` |

The integration profile raises `calls-per-client` to 100000, because every test class calls from 127.0.0.1
(`backend/src/test/resources/application-integration-test.yaml:12` – `:13`).

## 7. Tests

| Class | Cases |
|---|---|
| `AuthIntegrationTest` | 18 |
| `RefreshTokenIntegrationTest` | 12 |
| `SignInLimitIntegrationTest` | 9 |
| `PasswordUpgradeIntegrationTest` | 8, including a login shaped as §1.r's re-key left it (W-369) |
| `SignInAttemptsTest` | 6 |
| `SecurityAuditorAwareTest` | 3 |

`RouteAccessIntegrationTest` checks every route the app serves against the zone rules. The account erasure, consent, processing-log, breach,
purpose-lapse and re-application integration tests sign in through the module. No test cites a plan.

## 8. Known gaps (placed as W-380, D-905)

1. **Every origin may call the API** (`config/CrossOriginWebMvcConfigurer.java:10`, `allowedOrigins("*")`; `AuthConfig.java:55`). Tokens travel
   in a header, not a cookie, which limits the harm. A host (W-374) should name its origins.
2. **No password rule.** Neither registration nor a change checks length or strength; `RegistrationRequest` carries no constraint
   (`dto/RegistrationRequest.java:5` – `:12`), and neither does `AuthService`.
3. **The sign-in and registration bodies carry no constraint** (`dto/LoginRequest.java:5` – `:9`). `@Valid` has nothing to check, and what
   a missing email or password answers is not traced here.
4. **One body type for two routes.** The change of password reuses `RegistrationRequest`, so registration silently accepts `newPassword`
   and the change silently accepts `privacyNoticeVersion`.
5. **The answers' field names are mixed:** `tokenType` is camelCase beside snake_case fields (`LoginResponse.java:15`,
   `RefreshResponse.java:10`). Registration and a password change answer plain text naming the email (`AuthService.java:62`, `:88`).
6. **Dead weight.**
   - `AuthHelper.getRole` (`dto/AuthHelper.java:16` – `:18`) and `UserDetailsImpl.hasRole` (`service/UserDetailsImpl.java:66` – `:70`) have no
     caller.
   - Five of the nine roles (`MANAGER`, `EDITOR`, `AUTHOR`, `MODERATOR`, `GUEST`) are named by no code (`AuthHelper.java:4` – `:14`).
   - `AuthHelper` carries a banned suffix, grandfathered (`ArchitectureTest.java:56`).
7. **The login `_id` has two types in the data** (§4). W-369 removed every save, but the mismatch remains for the next one.
8. **Guessing limits live in one instance's memory** (§5). That is right for one instance (D-884 C1a), but not for more than one host.

## 9. Not covered here

- The zone and module prefix on every path (`ApiZones`), the error format and the request id: `shared`.
- Consent, the privacy notice, held accounts, the processing log and erasure as a whole: `shared`'s privacy package (W-237, W-250).
- How the owner grants a role (runbook §0.1) and the hash count after a deploy (runbook §1.2).
