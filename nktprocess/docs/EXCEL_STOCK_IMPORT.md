# Excel Stock-Master Import (`EXCEL_STOCK_MASTER_IMPORT`)

Bulk-imports Categories / Sub_Categories / Stocks / Stocks_units from an
uploaded `.xlsx` workbook into the existing `categories`, `sub_categories`
and `stocks` MongoDB collections, combining each stock with its units into
**one document per stock** with a nested `unit[]` array.

## 0. What was actually inspected first

Per the existing project (`nktprocess`, KadaiTheru/NammaOoruKadai backend),
before writing any code:

- `process-flow.json` — confirms `categories` and `stocks` are the real,
  already-used collections (`nkt.discover.categories`, `nkt.stores.products`,
  `nkt.order.place` via `NktOrderHandler`, `nkt.location.global_search`).
  There is a *separate* admin catalogue subsystem (`sections` / `subsections`
  / `products` / `brand` / `quantity` / `inventory`, driven by
  `nkt.add.category` etc.) — that is a different feature and this import does
  **not** touch it.
- No collection for sub-categories exists anywhere in `process-flow.json`.
  This import introduces `sub_categories`, matching the naming style of the
  project's other recent additions (`user_devices`, `store_staff_employees`).
- `NktDynamicRepository` — completely model-less (`Map<String,Object>`
  documents), no `insert`-or-`update` batch capability, no MongoDB
  transaction usage anywhere in the codebase.
- `pom.xml` — no Excel-reading library present.
- The single `/data` endpoint (`ProcessEngineController` /
  `ProcessEngineResource`) only accepts `data` (JSON) + `code` — no
  multipart support.
- `AppConfig` — only an `ObjectMapper` bean; no `MongoTransactionManager`.
- The Atlas connection string is `mongodb+srv://...` — a replica set, so
  multi-document transactions are actually available once a transaction
  manager bean exists.
- This project has no `admin` role (confirmed during the earlier FCM work) —
  only `customer` / `business` / `employee`. The import is gated to
  `business`, mirroring the precedent already set for FCM's group-broadcast
  endpoint.

### Reused as-is
`NktOperationHandler` (lambda pattern), `NktDynamicRepository` (all existing
methods), `NktCoreService.process()` (JWT auth / `allowedRoles` /
`RequiredFields`, completely unchanged), `process-flow.json`-driven
configuration, the `Map.of("statusCode",...,"statusDesc",...)` response
convention, the `str()`/`json()` handler-style helpers, `LocalDateTime.now().toString()`
for dates, the `@Component`-per-domain handler class pattern.

### New
`ExcelReaderUtil`, `ExcelStockImportService`, `MongoBulkUpsertService`,
`NktInventoryImportHandler`, `ExcelImportError`/`ExcelImportResult` (models),
one multipart endpoint (`POST /data/upload`), one small repository method
(`NktDynamicRepository.bulkUpsertByField`), one config bean
(`MongoTransactionManager`), one POI dependency.

### Modified
`NktCoreService` (handler registration), `NktProcessCodes`,
`process-flow.json`, `ProcessEngineResource`/`ProcessEngineController`,
`AppConfig`, `NktDynamicRepository`, `pom.xml`.

---

## 1. Request flow

```
POST /data/upload   (multipart/form-data)
  file = Store_maintenance_30-08-2026.xlsx
  code = nkt.inventory.excel_import
  data = {"token":"<JWT>"}
        │
        ▼
ProcessEngineController.processUpload()
  - reads file.getBytes() ONCE
  - dataMap = parse(data) + {"fileBytes": byte[], "fileName": String}
        │
        ▼
NktCoreService.process("nkt.inventory.excel_import", dataMap)
  - loads NktProcessDefinition from process-flow.json
  - validateRequiredFields()      (unchanged)
  - extractUserId() + JWT check   (unchanged)
  - validateRoles(["business"])   (unchanged)
  - dispatch CUSTOM -> handlers.get("EXCEL_STOCK_MASTER_IMPORT")
        │
        ▼
NktInventoryImportHandler.importStockMaster()
  - pulls fileBytes/fileName back out of data
  - checks file present / non-empty / .xlsx|.xls extension
        │
        ▼
ExcelStockImportService.importStockMaster(InputStream)
  Phase 1  READ    — ExcelReaderUtil reads exactly the 4 named sheets
  Phase 2  VALIDATE— every row, every sheet, ALL errors collected
  Phase 3  BUILD   — (only if zero errors) category/sub-category/stock docs
  Phase 4  WRITE   — MongoBulkUpsertService, one @Transactional method
        │
        ▼
JSON response (statusCode / statusDesc / errorCode / data.summary / data.errors[])
```

`/data` (the original endpoint) is untouched — this is a second, equally
thin endpoint, not a rewrite of the first.

## 2. Why a second endpoint instead of extending `/data`

`/data` is `@RequestParam("data") String, @RequestParam("code") String` —
JSON only. Rather than bolt multipart onto that contract (and risk breaking
every existing caller), `POST /data/upload` is a sibling that reads the file
once and folds it into the *same* `data` map (`fileBytes` as `byte[]`,
`fileName` as `String`) before calling the exact same
`NktCoreService.process()`. Every other `nkt.*` process code could in
principle be called through `/data/upload` too (the file fields are simply
absent for those) — nothing about auth, roles or validation forked.

## 3. Sheet → header mapping (verbatim from the real file)

| Sheet | Required headers | Notes |
|---|---|---|
| `Categories` | `categoryId`, `categoryName`, `name.en`, `name.ta`, `typeOfStore`, `status` | `description`, `icon[0].filename`, `icon[1].filename`, `icon[0].localPath`, `icon[1].localPath` optional |
| `Sub_Categories` | `subcategoryId` *(lowercase `c` — differs from Stocks' `subCategoryId`)*, `subCategoryName`, `name.en`, `name.ta`, `parentCategoryId`, `status` | `parentCategoryName`, icon columns optional |
| `Stocks` | `stockId`, `stockName`, `storeId`, `categoryId`, `subCategoryId`, `status`, `price`, `discount`, `discountPercentage`, `finalPrice` | `name.en`/`ta`, `nature`, `brand`, `image.filename[0]`/`[1]`, `image.localPath`, `storeName` pass through as-is |
| `Stocks_units` | `stockId`, `qty`, `qtyUnit` | `price`, `availableQty` optional per unit (frequently blank in the real file — meaning "use the stock's base price/availability") |

Any other sheet in the workbook (e.g. a stray "Notes" tab) is never read —
`ExcelReaderUtil.readSheet()` is only ever called with these four names.

## 4. MongoDB document mapping

**`categories`** (business key `categoryId`):
```json
{
  "_id": "...", "categoryId": "CAT_001", "categoryName": "Rice & Staples",
  "name": {"en": "Rice & Staples", "ta": "அரிசி & அத்தியாவசியப் பொருட்கள்"},
  "description": "Rice & Staples", "typeOfStore": "Supermarket",
  "status": "ACTIVE", "active": true,
  "icon": [{"filename": "cat_001_1.png", "localPath": "/assets/icons/categories/cat_001_1.png"}, ...],
  "createdAt": "2026-09-17T10:00:00", "updatedAt": "2026-09-17T10:00:00"
}
```
`active` is a **derived** compatibility field (`status == "ACTIVE"`), added
because `nkt.discover.categories` already filters on `StaticCriteria
{"active": true}` — without it, imported categories would be invisible to
the existing discover endpoint. This is a structural bridge, not invented
business data.

**`sub_categories`** (business key `subCategoryId`):
```json
{
  "_id": "...", "subCategoryId": "SUBCAT_001", "subCategoryName": "Raw Rice",
  "name": {"en": "Raw Rice", "ta": "..."},
  "categoryId": "CAT_001", "categoryName": "Rice & Staples",
  "status": "ACTIVE", "icon": [...],
  "createdAt": "...", "updatedAt": "..."
}
```
`categoryId`/`categoryName` come from the sheet's `parentCategoryId` /
`parentCategoryName` columns — `subcategory.categoryId -> category.categoryId`.

**`stocks`** (business key `stockId`) — matches exactly what
`NktOrderHandler` already reads (`stock.get("unit")` as a list of maps with
`qty`/`price`/`availableQty`):
```json
{
  "_id": "...", "stockId": "ST011_PROD001", "stockName": "Raw rice",
  "name": {"en": "Raw rice", "ta": "பச்சை அரிசி"},
  "storeId": "ST011", "storeName": "Anandham Supermarket",
  "categoryId": "CAT_001", "subCategoryId": "SUBCAT_001",
  "nature": "packed", "brand": "Store", "status": "ACTIVE",
  "price": 50, "discount": 0, "discountPercentage": 0, "finalPrice": 50,
  "image": {"filenames": ["st011_prod001_1.png", "st011_prod001_2.png"],
            "localPath": "/assets/images/stocks/st011_prod001"},
  "unit": [
    {"qty": 1,  "qtyUnit": "Kg", "unit": "1Kg",  "price": 45, "availableQty": 10},
    {"qty": 26, "qtyUnit": "Kg", "unit": "26Kg", "price": 45, "availableQty": 10}
  ],
  "createdAt": "...", "updatedAt": "..."
}
```
Each `unit[]` entry keeps the exact `qty`/`price`/`availableQty` keys
`NktOrderHandler` reads today, and adds `qtyUnit` (raw, e.g. `"Kg"`) and
`unit` (the merged display string, e.g. `"1Kg"`) alongside them.

## 5. Stock + units merge logic

- `Stocks_units` is grouped by `stockId` in a single pass (`Map<String,
  List<...>>`) before any stock document is built — the 3,953-row units
  sheet is read once, not once per stock (avoids N+1).
- `unit = format(qty) + qtyUnit.trim()`. `format(qty)` strips a trailing
  `.0` (`1` not `1.0`) but keeps real decimals (`0.5g`, `28.2g`) — works
  for every unit type in the file generically (`Kg`, `g`, `ml`, `L`,
  `Pack`, `Pcs`, and anything else), nothing is hardcoded to a specific
  unit name — including the real file's `"Mrp"` and other odd values,
  which are passed through faithfully rather than special-cased.
- If a `qty` cell is already a combined string (e.g. `"1Kg"`) and ends with
  the row's own `qtyUnit`, the numeric prefix is reused as-is — it is never
  re-appended into `"1KgKg"`.
- A `qty` cell that isn't a number and isn't a recognisable combined string
  (the real file has two `"—"` placeholder rows) **fails validation** — it
  is not silently dropped or guessed at.
- On **update**, the whole `unit[]` array is replaced with the latest
  Excel data (via a MongoDB `$set` on the `unit` field) — not merged or
  appended. The task brief explicitly prefers this over an
  inventory-preservation rule, and this project has no existing
  reservation/hold logic on `unit[]` that would be lost by a full replace.

## 6. Validation strategy — strict, all-or-nothing

**READ ALL → VALIDATE ALL → IF VALID → WRITE.** A single bad row anywhere
means *nothing* is written; the response lists every error found in one
pass (not just the first), so the source file can be fixed once.

| Check | Applies to |
|---|---|
| Sheet present | all 4 sheets — missing any one fails immediately |
| Required fields non-blank | every business field named in §3 |
| Duplicate business key within its own sheet | `categoryId`, `subcategoryId`, `stockId` |
| Duplicate `(stockId, qty, qtyUnit)` | `Stocks_units` |
| Referential integrity | `Sub_Categories.parentCategoryId → Categories.categoryId`; `Stocks.categoryId/subCategoryId → Categories/Sub_Categories`; `Stocks_units.stockId → Stocks.stockId` |
| Numeric + non-negative | `price`, `discount`, `discountPercentage` (0–100), `finalPrice` on Stocks; `qty` (required), `price`/`availableQty` (optional but must be numeric if present) on Stocks_units |
| Valid `status` | `ACTIVE` / `INACTIVE` (case-insensitive) on all three entities |

A stock with **zero** unit rows is allowed (`unit: []`) — the brief lists it
as a scenario to test, not as a rule to reject; nothing in the requirements
says a stock must have at least one purchasable unit to be catalogue-valid.

### A real finding on the uploaded file

Re-running validation against the actual
`Store_maintenance_30-08-2026_java_code.xlsx`: referential integrity is
**100% clean** (every categoryId/subCategoryId Stocks references really
exists in Categories/Sub_Categories — an earlier read of this file, before
this session's inspection script was fixed, had incorrectly reported 2,371
broken references; that was a bug in the *inspection script*, not the data).
What the file genuinely has is **1,937 Stocks rows with a blank
categoryId/subCategoryId**, plus **434 draft rows** with only a
`stockId`/name and nothing else. Under the strict all-or-nothing rule
above, importing this exact file today returns a validation-failure report
listing those rows rather than writing anything — which is the correct,
literal behavior of "the Excel is the source of truth" + "no partial
writes", not a bug in the importer. The source sheet needs those rows
completed (or removed) before the first successful import.

## 7. Upsert strategy — atomic, batched, no N+1

`NktDynamicRepository.bulkUpsertByField(collection, keyField, documents,
setOnInsertFields)` is the one repository addition. For each document it
issues `BulkOperations.upsert(Query.where(keyField).is(value), update)`:
every field is `$set` **except** `createdAt`, which is `$setOnInsert` — so
MongoDB itself decides insert-vs-update semantics atomically per document:
a brand-new document gets `createdAt` once; an existing one keeps its
original `createdAt` and only `updatedAt` changes. `_id` is preserved
automatically by Mongo's own upsert behavior — nothing in this code ever
looks it up or copies it by hand. This also means **no pre-fetch of
existing documents is needed at all** (not for 2,734 stocks, not for 26
categories) — one `bulkOps(...).execute()` call per collection.

## 8. Transaction strategy

`AppConfig` gains one `MongoTransactionManager` bean (the Atlas cluster is
a replica set, which Mongo requires for multi-document transactions —
confirmed from the `mongodb+srv://` URI). `MongoBulkUpsertService.
upsertImport()` is `@Transactional("mongoTransactionManager")` and performs
all three bulk upserts (categories, sub_categories, stocks) inside it. It
is deliberately its own Spring bean, not a method on
`ExcelStockImportService`: `@Transactional` only takes effect through the
Spring proxy, and a self-invoked method on the same bean would silently run
non-transactionally — a classic Spring AOP pitfall this design avoids
outright. If any of the three bulk writes fails, the whole import rolls
back.

## 9. File placement in the existing project

```
src/main/java/com/models/excel/ExcelImportError.java          NEW
src/main/java/com/models/excel/ExcelImportResult.java         NEW
src/main/java/com/service/ExcelReaderUtil.java                 NEW
src/main/java/com/service/ExcelStockImportService.java         NEW
src/main/java/com/service/MongoBulkUpsertService.java           NEW
src/main/java/com/service/handlers/NktInventoryImportHandler.java NEW
src/main/java/com/repository/NktDynamicRepository.java         MODIFIED (+bulkUpsertByField)
src/main/java/com/configs/AppConfig.java                       MODIFIED (+MongoTransactionManager)
src/main/java/com/service/NktCoreService.java                  MODIFIED (+handler registration)
src/main/java/com/constant/NktProcessCodes.java                MODIFIED (+EXCEL_STOCK_MASTER_IMPORT)
src/main/java/com/resource/ProcessEngineResource.java          MODIFIED (+processUpload)
src/main/java/com/controller/ProcessEngineController.java      MODIFIED (+processUpload)
src/main/resources/process-flow.json                           MODIFIED (+nkt.inventory.excel_import)
pom.xml                                                         MODIFIED (+poi-ooxml 5.3.0)
src/test/java/com/service/ExcelReaderUtilTest.java              NEW
src/test/java/com/service/ExcelStockImportServiceTest.java      NEW
src/test/java/com/service/handlers/NktInventoryImportHandlerTest.java NEW
```

`application.yml` is **not** modified — the existing 5 MB multipart limit
comfortably covers the real file (≈470 KB).

## 10. Example requests / responses

See `docs/postman_excel_stock_import.json` for a ready-to-import Postman
collection. Curl equivalent:

```bash
curl -X POST http://localhost:8070/data/upload \
  -F "file=@Store_maintenance_30-08-2026.xlsx;type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
  -F 'code=nkt.inventory.excel_import' \
  -F 'data={"token":"<BUSINESS_JWT>"}'
```

**Success:**
```json
{
  "data": {
    "summary": {
      "categoriesRows": 26, "subCategoriesRows": 84, "stocksRows": 2734, "unitsRows": 3953,
      "totalErrors": 0,
      "categoriesInserted": 26, "categoriesUpdated": 0,
      "subCategoriesInserted": 84, "subCategoriesUpdated": 0,
      "stocksInserted": 2734, "stocksUpdated": 0,
      "totalUnitsImported": 3953
    }
  },
  "statusCode": "N200",
  "statusDesc": "Stock master import completed successfully"
}
```

**Validation failure:**
```json
{
  "data": {
    "summary": {"categoriesRows": 26, "subCategoriesRows": 84, "stocksRows": 2734, "unitsRows": 3953, "totalErrors": 1937},
    "errors": [
      {"sheet": "Stocks", "row": 366, "stockId": "ST011_PROD364", "field": "categoryId", "message": "categoryId is required"},
      {"sheet": "Stocks", "row": 366, "stockId": "ST011_PROD364", "field": "subCategoryId", "message": "subCategoryId is required"}
    ]
  },
  "statusCode": "N400",
  "statusDesc": "Validation failed — nothing was written to the database. Fix the listed rows and re-upload.",
  "errorCode": "EXCEL_VALIDATION_FAILED"
}
```

## 11. Build note

Maven Central is not reachable from this sandbox (org egress policy — see
the FCM integration's docs for the same limitation), so `poi-ooxml` could
not be resolved and `mvn compile`/`mvn test` could not be run here. All
files were verified with a brace/paren/bracket balance check and JSON/XML
validity checks; please run, on your machine:

```bash
./mvnw -q -DskipTests compile
./mvnw -q -Dtest=Excel*,NktInventoryImportHandlerTest test
```
