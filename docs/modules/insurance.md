# Module `insurance` — stored insurance policies (no routes)

> W-357b2 (D-902), written from the code at `review/taxplanning` on 2026-11-22. Every claim cites the line it was read from. Template:
> `docs/taxplanning-build/plans/W-357b.md` C2a.

## 1. Purpose

The module holds a person's insurance policies, the agent's details among them, in one collection. **Today nothing writes or reads them
through the app.** The only code that touches the collection erases a person's policies with their account. The module exists so that rows
stored by earlier versions are erased and encrypted like the rest of a person's data
(`insurance/service/InsuranceRecordErasure.java:12` – `:15`).

## 2. Boundaries

- `OPEN` module, `allowedDependencies = {"shared"}` (`backend/src/main/java/com/thiru/wealthlens/insurance/package-info.java:1` – `:5`).
- It uses `shared`'s auditing, encryption converters and `PersonalDataHolder` (`InsuranceEntity.java:7` – `:10`,
  `InsuranceRecordErasure.java:7`). No other module imports from `insurance` (grep).
- It has no URL segment: `ApiZones`' `MODULE_PATHS` names no `insurance` (`shared/web/ApiZones.java:26` – `:27`), because it has no
  controller.

## 3. Routes

None.

## 4. Stored data

Collection `insurances` (`insurance/entity/InsuranceEntity.java:22`), one document per policy:

| Field | Stored as | Where |
|---|---|---|
| `_id` | `@MongoId String` | `InsuranceEntity.java:28` – `:30` |
| `email` | encrypted, searchable (`SearchableIdentifier`) | `:32` – `:34` |
| `insurance_id`, `agent_name`, `agent_email`, `agent_contact`, `agent_address`, `notes` | encrypted (`EncryptedField`) | `:39` – `:41`, `:60` – `:76` |
| `port_from`, `port_to`, `insurance_type` (`HEALTH_INSURANCE`, `TERM_INSURANCE`, `VEHICLE_INSURANCE`, `LIFE_INSURANCE`, `GENERAL_INSURANCE`), `policy_type` (`SINGLE`, `GROUP`), `premium`, `start_date`, `end_date`, `maturity_date`, `renewal_date`, `maturity_amount`, `broker`, `insurance_status` | plain; the four dates and `maturity_amount` are **strings** | `:35` – `:59`, `:72` – `:73`; `dto/enums/InsuranceType.java:3` – `:9`; `dto/enums/PolicyType.java:3` – `:6` |
| `policyDetails[]` | each `PolicyDetails`: `policy_id`, the four `agent_*`, `notes` and `policy_description` encrypted; `premium`, `start_date` and `end_date` (dates), `policy_name`, `broker_name`, `insurance_status` plain. The list itself has no `@Field` name, so it is stored as `policyDetails` | `:78`; `entity/model/PolicyDetails.java:12` – `:56` |
| `audit_metadata` | who created and last changed it (actor encrypted) | `:80` – `:82` |

No index is declared on the entity.

## 5. Behaviour

**Erasure** (`InsuranceRecordErasure.java:16` – `:31`): as a `PersonalDataHolder` named `"insurance"` in the erasure notice (`:22` – `:25`),
it removes every policy whose `email` is the person's (`:27` – `:30`) when their account is erased (W-237d). The query names the entity class,
so the email is matched through its converter.

Nothing else runs.

## 6. Configuration

None.

## 7. Tests

No test names the module's classes or its collection (grep of `backend/src/test` for `InsuranceEntity`, `InsuranceRecordErasure`,
`insurances`). The erasure runs inside the account-erasure flow, but no test puts an insurance row in front of it.

## 8. Known gaps (placed as W-373, D-902)

1. **Nothing writes or reads policies**, so the collection is reachable only by erasure. Either the feature is built (routes, a request type)
   or the module is removed with its data's erasure kept. That is a product decision, not a doc one.
2. **Dead weight.** `InsuranceService` is an empty class (`service/InsuranceService.java:5` – `:9`). `InsuranceRequest` and
   `InsuranceResponse` are used by no code (grep).
3. **Dates and an amount stored as strings** (`InsuranceEntity.java:48` – `:57`), where `PolicyDetails` uses `LocalDate` for the same
   facts (`PolicyDetails.java:19` – `:23`).
4. **No test reaches the erasure of an insurance row** that this doc could cite.

## 9. Not covered here

- The encryption converters, auditing and the erasure flow that calls `PersonalDataHolder`s: `shared` (W-248, W-237d).
