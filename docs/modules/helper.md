# Module `helper` — the transaction upload template, and file answers

> W-357b3 (D-903), written from the code at `review/taxplanning` on 2026-11-22. Every claim cites the line it was read from. Template:
> `docs/taxplanning-build/plans/W-357b.md` C2a.

## 1. Purpose

The module gives a person the blank spreadsheet they fill in to upload trades, and the list of its columns. It also holds the small types
that turn an exported file into an HTTP attachment (`FileStream`, `FileType`, `FileHelper`), which `portfolio`'s exports use too. It also
ships a sample shipping-label page and PDF that have nothing to do with WealthLens (gap 1).

## 2. Boundaries

- `OPEN` module, `allowedDependencies = {"shared", "portfolio"}` (`backend/src/main/java/com/thiru/wealthlens/helper/package-info.java:1` – `:5`).
- It calls `portfolio`'s `EntityExportService` for the template (`helper/controller/HelperController.java:5`, `:21`).
- **`portfolio` depends back on it.** `EntityExportController` and `EntityExportService` import `helper.file.FileStream`, `FileType` and
  `FileHelper` (`portfolio/controller/EntityExportController.java:3` – `:4`, `portfolio/service/EntityExportService.java:3` – `:4`), and
  `portfolio` lists `helper` (`portfolio/package-info.java:3`). The two modules depend on each other (gap 6).
- URL segment `templates` (`shared/web/ApiZones.java:26` – `:27`).

## 3. Routes

All four are in the **public** zone (`@PublicApi`): no token.

| Method and path | Answer | Where |
|---|---|---|
| `GET /api/v1/public/templates` | `transactions-template.xlsx` as an attachment: the upload form with one sample row | `HelperController.java:23` – `:31`; `EntityExportService.java:57` – `:60` |
| `GET /api/v1/public/templates/fields` | a JSON array of 14 column names | `HelperController.java:33` – `:38`; `shared/util/transaction/ExcelHeaders.java:33` – `:36` |
| `GET /api/v1/public/templates/generate-label` | the text `shipping-label` | `TemplateController.java:25` – `:30` |
| `GET /api/v1/public/templates/download-label` | `shipping-label.pdf`: `templates/shipping-label.html` rendered with fixed sample data | `TemplateController.java:32` – `:58`, `:60` – `:90` |

No route takes a parameter or a body, so none refuses a caller.

## 4. Stored data

None.

## 5. Behaviour

- **The template.** `HelperController` asks the export for the entity `transactions-template` with an empty `UserMail`
  (`HelperController.java:26` – `:28`). The export answers a fixed file name, not a dated one, because it is a blank form
  (`EntityExportService.java:57` – `:60`).
- **An attachment.** `FileHelper.sendFileAsAttachment` sets `Content-Disposition: attachment; filename=…` and the file type's media type
  (`helper/file/FileHelper.java:9` – `:14`). `FileType` has `XLSX` and `XLS`, both with the media type `application/vnd.ms-excel`
  (`helper/file/FileType.java:9` – `:10`).
- **The fields.** The 14 names are `EMAIL`, `STOCK CODE`, `STOCK NAME`, `EXCHANGE NAME`, `BROKER NAME`, `ASSET TYPE`, `MATURITY DATE`,
  `PRICE`, `QUANTITY`, `TRANSACTION TYPE`, `TRANSACTION DATE`, `BROKER CHARGES`, `MISC CHARGES` and `COMMENTS`
  (`ExcelHeaders.java:33` – `:36`, values at `:5` – `:26`).
- **The upload reads a different set** (`portfolio/service/parser/AssetRequestParser.java:58` – `:87`). It reads the 13 listed after
  `EMAIL`, and also `ACCOUNT HOLDER`, `SEGMENT`, `ACCOUNT TYPE`, `ORDER ID`, `ORDER EXECUTION TIME` and `TIME ZONE`. It does not read
  `EMAIL` (gap 3).

## 6. Configuration

None of its own. The label page is a Thymeleaf template found by Spring's defaults (`TemplateController.java:19` – `:23`, `:40`).

## 7. Tests

No test and no api-collection request reaches any of the four routes (grep of `backend/src/test` and `api-collection` for their paths).
`RouteAccessIntegrationTest` only checks that they sit in the public zone.

## 8. Known gaps (placed as W-372, D-903)

1. **A sample shipping label is served publicly.**
   - `/generate-label` and `/download-label` render fixed sample data (a sender in Boston and a recipient in San Francisco,
     `TemplateController.java:60` – `:90`) and belong to no WealthLens feature.
   - `/generate-label` is in a `@RestController`, so it answers the literal text `shipping-label` instead of the page (`:25` – `:30`).
   - `ShippingLabel` (`shared/dto/ShippingLabel.java`) and `templates/shipping-label.html` exist only for them.
2. **No test reaches the template or the fields route.**
3. **`/fields` and the upload disagree.** The list names `EMAIL`, which the upload ignores, and omits six columns the upload reads (§5).
   A person filling the form from `/fields` cannot give a segment, an account holder or an order time.
4. **The `.xlsx` media type is the `.xls` one** (`FileType.java:9`). The xlsx type is
   `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
5. **`null` passed across a boundary** (CLAUDE.md §2): `UserMail.from(null)` (`HelperController.java:28`).
6. **Code standards:**
   - `helper` and `portfolio` depend on each other.
   - A star import (`TemplateController.java:7`).
   - A hand-written constructor (`:21` – `:23`; CLAUDE.md asks for `@RequiredArgsConstructor`).
   - `FileHelper` carries a suffix CLAUDE.md bans in new code, and `FileType.contentType` is `null` for both values and read by no code (grep for `getContentType` on a
     `FileType`).

## 9. Not covered here

- The exports themselves (`EntityExportService`, `PortfolioExcelExporter`) and the upload: `portfolio`.
- The column names: `shared/util/transaction/ExcelHeaders`, `shared`.
