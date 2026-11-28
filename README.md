# WealthLens

A portfolio tracker for the Indian stock market: buy and sell transactions, corporate actions (bonus, split, demerger), profit
and loss, broker and fund charges, insurance, and income-tax planning. One Spring Boot 4.1 application (Java 25, Spring
Modulith, Maven), storing its data in PostgreSQL (schema `wealthlens`, made by Flyway).

## Modules

Each module is a package under `com.thiru.wealthlens` whose `package-info.java` names the modules it may use;
`WealthLensModulithTest` checks the boundaries.

| Module | Owns |
|---|---|
| `portfolio` | trades (two flows, V1 and V2), holdings, realised profit and loss, the Excel upload and download |
| `corporate` | corporate actions and their processing per quarter |
| `brokercharges` | the charge engine: rate cards, the charge catalogue, charges recorded per trade, AMC |
| `taxplanning` | taxpayer profiles, tax-year workspaces, filing drafts, computations, documents |
| `taxengine` | the tax computation itself, from versioned rule packs (`src/main/resources/rulepacks/`) |
| `auth` | registration, sign-in, access and refresh tokens |
| `insurance`, `finance`, `helper` | insurance policies; financial calculators (SIP, interest); the upload templates and file handling |
| `shared` | errors, route zones, privacy records, audit columns, utilities |

## Build, run, test

```bash
./mvnw clean install -pl backend -am -DskipTests   # build
./mvnw spring-boot:run -pl backend                 # run (dev profile; -Dspring-boot.run.profiles=staging|prod)
./mvnw test -pl backend -Punit                     # unit tests, no Docker
./mvnw test -pl backend -Pintegration              # integration tests (Docker: Testcontainers PostgreSQL)
./mvnw clean verify                                # everything CI runs, with the coverage gates
```

The app needs `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECURITY_KEY` and `PRIVACY_CONTACT`.
`./mvnw spotless:apply` formats the code; Checkstyle and Spotless fail an unformatted build. The full list of commands and
the build's rules are in [`CLAUDE.md`](CLAUDE.md).

## The API

Every route is `/api/v1/{public|me|admin}/{module}/…`; errors are RFC 9457 problem details. The OpenAPI spec is served at
`GET /api/v1/admin/docs/openapi` (`SUPER_USER`), and [`api-collection/`](api-collection) holds a request for every route.

## Documentation

- [`docs/README.md`](docs/README.md) — the index of every doc.
- [`docs/modules/`](docs/modules) — one current doc per module, written from the code and citing it.
- [`docs/taxplanning-build/post-deployment-runbook.md`](docs/taxplanning-build/post-deployment-runbook.md) — what a
  database still needs at a deploy.

Earlier versions of this README and of the docs are in git tags `docs-history-m1` – `docs-history-m4`.
