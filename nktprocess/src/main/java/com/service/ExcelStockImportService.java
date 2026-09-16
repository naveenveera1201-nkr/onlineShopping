package com.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;

import com.models.excel.ExcelImportError;
import com.models.excel.ExcelImportResult;
import com.mongodb.bulk.BulkWriteResult;
import com.service.ExcelReaderUtil.ExcelRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core logic for {@code EXCEL_STOCK_MASTER_IMPORT}: reads the 4 required
 * sheets, validates everything up front, and — only if the whole workbook is
 * valid — writes categories / sub_categories / stocks in one transaction.
 *
 * Strict two-phase contract, exactly as specified: READ ALL → VALIDATE ALL →
 * IF VALID → WRITE. A single bad row anywhere in the workbook means nothing
 * is written at all; the caller gets back every error found, not just the
 * first one, so the source Excel can be fixed in one pass instead of a
 * back-and-forth of single-row rejections.
 *
 * This class never invents, renames or recalculates business data. Every
 * field on a category/sub-category/stock document is either copied verbatim
 * from its Excel column, or is a pure structural transform explicitly asked
 * for (Stocks + Stocks_units → one stock document with a nested unit[]).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelStockImportService {

    private static final String SHEET_CATEGORIES = "Categories";
    private static final String SHEET_SUBCATEGORIES = "Sub_Categories";
    private static final String SHEET_STOCKS = "Stocks";
    private static final String SHEET_UNITS = "Stocks_units";

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final ExcelReaderUtil excelReader;
    private final MongoBulkUpsertService bulkUpsertService;

    public ExcelImportResult importStockMaster(InputStream excelStream) {

        Workbook workbook;
        try {
            workbook = excelReader.open(excelStream);
        } catch (Exception e) {
            log.warn("EXCEL_STOCK_MASTER_IMPORT: could not open workbook: {}", e.getMessage());
            return ExcelImportResult.failure(
                    List.of(ExcelImportError.of("workbook", 0, null, "file",
                            "Could not read the uploaded file as an Excel (.xlsx) workbook: " + e.getMessage())),
                    Map.of("sheetsRead", 0));
        }

        try {
            return doImport(workbook);
        } finally {
            try {
                workbook.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Phase 1 — read
    // ─────────────────────────────────────────────────────────────────────

    private ExcelImportResult doImport(Workbook workbook) {

        List<ExcelRow> catRows = excelReader.readSheet(workbook, SHEET_CATEGORIES);
        List<ExcelRow> subRows = excelReader.readSheet(workbook, SHEET_SUBCATEGORIES);
        List<ExcelRow> stockRows = excelReader.readSheet(workbook, SHEET_STOCKS);
        List<ExcelRow> unitRows = excelReader.readSheet(workbook, SHEET_UNITS);

        List<String> missingSheets = new ArrayList<>();
        if (catRows == null) missingSheets.add(SHEET_CATEGORIES);
        if (subRows == null) missingSheets.add(SHEET_SUBCATEGORIES);
        if (stockRows == null) missingSheets.add(SHEET_STOCKS);
        if (unitRows == null) missingSheets.add(SHEET_UNITS);

        if (!missingSheets.isEmpty()) {
            List<ExcelImportError> errors = missingSheets.stream()
                    .map(s -> ExcelImportError.of(s, 0, null, "sheet",
                            "Required sheet \"" + s + "\" was not found in the uploaded workbook"))
                    .toList();
            return ExcelImportResult.failure(errors, Map.of(
                    "sheetsFound", 4 - missingSheets.size(),
                    "sheetsRequired", 4,
                    "missingSheets", missingSheets));
        }

        if (catRows.isEmpty() && subRows.isEmpty() && stockRows.isEmpty() && unitRows.isEmpty()) {
            return ExcelImportResult.failure(
                    List.of(ExcelImportError.of("workbook", 0, null, "data",
                            "The workbook has no data rows in Categories, Sub_Categories, Stocks or Stocks_units")),
                    Map.of("categoriesRows", 0, "subCategoriesRows", 0, "stocksRows", 0, "unitsRows", 0));
        }

        // ─────────────────────────────────────────────────────────────────
        // Phase 2 — validate (collect every error before deciding anything)
        // ─────────────────────────────────────────────────────────────────

        List<ExcelImportError> errors = new ArrayList<>();

        Set<String> knownCategoryIds = new LinkedHashSet<>();
        validateCategories(catRows, errors, knownCategoryIds);

        Set<String> knownSubCategoryIds = new LinkedHashSet<>();
        validateSubCategories(subRows, errors, knownCategoryIds, knownSubCategoryIds);

        Set<String> knownStockIds = new LinkedHashSet<>();
        validateStocks(stockRows, errors, knownCategoryIds, knownSubCategoryIds, knownStockIds);

        Map<String, List<Map<String, Object>>> unitsByStockId = new HashMap<>();
        validateUnits(unitRows, errors, knownStockIds, unitsByStockId);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("categoriesRows", catRows.size());
        summary.put("subCategoriesRows", subRows.size());
        summary.put("stocksRows", stockRows.size());
        summary.put("unitsRows", unitRows.size());
        summary.put("totalErrors", errors.size());

        if (!errors.isEmpty()) {
            return ExcelImportResult.failure(errors, summary);
        }

        // ─────────────────────────────────────────────────────────────────
        // Phase 3 — build documents (only reachable when the whole workbook is valid)
        // ─────────────────────────────────────────────────────────────────

        List<Map<String, Object>> categoryDocs = catRows.stream()
                .map(this::buildCategoryDoc).toList();
        List<Map<String, Object>> subCategoryDocs = subRows.stream()
                .map(this::buildSubCategoryDoc).toList();
        List<Map<String, Object>> stockDocs = stockRows.stream()
                .map(row -> buildStockDoc(row, unitsByStockId.getOrDefault(str(row.values(), "stockId"), List.of())))
                .toList();

        long totalUnitsImported = stockDocs.stream()
                .mapToLong(d -> ((List<?>) d.get("unit")).size())
                .sum();

        // ─────────────────────────────────────────────────────────────────
        // Phase 4 — write (single transaction across all three collections)
        // ─────────────────────────────────────────────────────────────────

        Map<String, BulkWriteResult> writeResults =
                bulkUpsertService.upsertImport(categoryDocs, subCategoryDocs, stockDocs);

        summary.put("categoriesInserted", upsertedCount(writeResults, "categories"));
        summary.put("categoriesUpdated", matchedCount(writeResults, "categories"));
        summary.put("subCategoriesInserted", upsertedCount(writeResults, "sub_categories"));
        summary.put("subCategoriesUpdated", matchedCount(writeResults, "sub_categories"));
        summary.put("stocksInserted", upsertedCount(writeResults, "stocks"));
        summary.put("stocksUpdated", matchedCount(writeResults, "stocks"));
        summary.put("totalUnitsImported", totalUnitsImported);

        log.info("EXCEL_STOCK_MASTER_IMPORT complete: {}", summary);

        return ExcelImportResult.success(summary);
    }

    private static long upsertedCount(Map<String, BulkWriteResult> results, String collection) {
        BulkWriteResult r = results.get(collection);
        return r == null ? 0 : r.getUpserts().size();
    }

    private static long matchedCount(Map<String, BulkWriteResult> results, String collection) {
        BulkWriteResult r = results.get(collection);
        if (r == null) return 0;
        // matchedCount counts every pre-existing document the upsert query matched
        // (whether or not any field actually changed); MongoDB never double-counts a
        // matched document as an upsert, so this is already disjoint from getUpserts().
        return r.getMatchedCount();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation — Categories
    // ─────────────────────────────────────────────────────────────────────

    private void validateCategories(List<ExcelRow> rows, List<ExcelImportError> errors, Set<String> knownIds) {
        Set<String> seen = new HashSet<>();
        for (ExcelRow row : rows) {
            Map<String, Object> v = row.values();
            String categoryId = str(v, "categoryId");
            String categoryName = str(v, "categoryName");
            String nameEn = str(nested(v), "en");
            String typeOfStore = str(v, "typeOfStore");
            String status = str(v, "status");

            if (isBlank(categoryId)) {
                errors.add(err(SHEET_CATEGORIES, row, categoryId, "categoryId", "categoryId is required"));
            } else {
                if (!seen.add(categoryId)) {
                    errors.add(err(SHEET_CATEGORIES, row, categoryId, "categoryId",
                            "Duplicate categoryId within the Categories sheet"));
                }
                knownIds.add(categoryId);
            }
            if (isBlank(categoryName)) {
                errors.add(err(SHEET_CATEGORIES, row, categoryId, "categoryName", "categoryName is required"));
            }
            if (isBlank(nameEn)) {
                errors.add(err(SHEET_CATEGORIES, row, categoryId, "name.en", "name.en is required"));
            }
            if (isBlank(typeOfStore)) {
                errors.add(err(SHEET_CATEGORIES, row, categoryId, "typeOfStore", "typeOfStore is required"));
            }
            validateStatus(SHEET_CATEGORIES, row, categoryId, status, errors);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation — Sub_Categories
    // ─────────────────────────────────────────────────────────────────────

    private void validateSubCategories(List<ExcelRow> rows, List<ExcelImportError> errors,
                                        Set<String> knownCategoryIds, Set<String> knownSubCategoryIds) {
        Set<String> seen = new HashSet<>();
        for (ExcelRow row : rows) {
            Map<String, Object> v = row.values();
            String subCategoryId = str(v, "subcategoryId");
            String subCategoryName = str(v, "subCategoryName");
            String nameEn = str(nested(v), "en");
            String parentCategoryId = str(v, "parentCategoryId");
            String status = str(v, "status");

            if (isBlank(subCategoryId)) {
                errors.add(err(SHEET_SUBCATEGORIES, row, subCategoryId, "subcategoryId", "subcategoryId is required"));
            } else {
                if (!seen.add(subCategoryId)) {
                    errors.add(err(SHEET_SUBCATEGORIES, row, subCategoryId, "subcategoryId",
                            "Duplicate subcategoryId within the Sub_Categories sheet"));
                }
                knownSubCategoryIds.add(subCategoryId);
            }
            if (isBlank(subCategoryName)) {
                errors.add(err(SHEET_SUBCATEGORIES, row, subCategoryId, "subCategoryName", "subCategoryName is required"));
            }
            if (isBlank(nameEn)) {
                errors.add(err(SHEET_SUBCATEGORIES, row, subCategoryId, "name.en", "name.en is required"));
            }
            if (isBlank(parentCategoryId)) {
                errors.add(err(SHEET_SUBCATEGORIES, row, subCategoryId, "parentCategoryId", "parentCategoryId is required"));
            } else if (!knownCategoryIds.contains(parentCategoryId)) {
                errors.add(err(SHEET_SUBCATEGORIES, row, subCategoryId, "parentCategoryId",
                        "parentCategoryId \"" + parentCategoryId + "\" does not match any categoryId in the Categories sheet"));
            }
            validateStatus(SHEET_SUBCATEGORIES, row, subCategoryId, status, errors);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation — Stocks
    // ─────────────────────────────────────────────────────────────────────

    private void validateStocks(List<ExcelRow> rows, List<ExcelImportError> errors,
                                 Set<String> knownCategoryIds, Set<String> knownSubCategoryIds,
                                 Set<String> knownStockIds) {
        Set<String> seen = new HashSet<>();
        for (ExcelRow row : rows) {
            Map<String, Object> v = row.values();
            String stockId = str(v, "stockId");
            String stockName = str(v, "stockName");
            String storeId = str(v, "storeId");
            String categoryId = str(v, "categoryId");
            String subCategoryId = str(v, "subCategoryId");
            String status = str(v, "status");

            if (isBlank(stockId)) {
                errors.add(err(SHEET_STOCKS, row, null, "stockId", "stockId is required"));
            } else {
                if (!seen.add(stockId)) {
                    errors.add(err(SHEET_STOCKS, row, stockId, "stockId", "Duplicate stockId within the Stocks sheet"));
                }
                knownStockIds.add(stockId);
            }
            if (isBlank(stockName)) {
                errors.add(err(SHEET_STOCKS, row, stockId, "stockName", "stockName is required"));
            }
            if (isBlank(storeId)) {
                errors.add(err(SHEET_STOCKS, row, stockId, "storeId", "storeId is required"));
            }
            if (isBlank(categoryId)) {
                errors.add(err(SHEET_STOCKS, row, stockId, "categoryId", "categoryId is required"));
            } else if (!knownCategoryIds.contains(categoryId)) {
                errors.add(err(SHEET_STOCKS, row, stockId, "categoryId",
                        "categoryId \"" + categoryId + "\" does not match any categoryId in the Categories sheet"));
            }
            if (isBlank(subCategoryId)) {
                errors.add(err(SHEET_STOCKS, row, stockId, "subCategoryId", "subCategoryId is required"));
            } else if (!knownSubCategoryIds.contains(subCategoryId)) {
                errors.add(err(SHEET_STOCKS, row, stockId, "subCategoryId",
                        "subCategoryId \"" + subCategoryId + "\" does not match any subcategoryId in the Sub_Categories sheet"));
            }
            validateStatus(SHEET_STOCKS, row, stockId, status, errors);

            requireNonNegativeNumber(SHEET_STOCKS, row, stockId, "price", v.get("price"), errors, false);
            requireNonNegativeNumber(SHEET_STOCKS, row, stockId, "discount", v.get("discount"), errors, false);
            requireNonNegativeNumber(SHEET_STOCKS, row, stockId, "discountPercentage", v.get("discountPercentage"), errors, false);
            requireNonNegativeNumber(SHEET_STOCKS, row, stockId, "finalPrice", v.get("finalPrice"), errors, false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation — Stocks_units (+ grouping by stockId, done in the same pass)
    // ─────────────────────────────────────────────────────────────────────

    private void validateUnits(List<ExcelRow> rows, List<ExcelImportError> errors, Set<String> knownStockIds,
                                Map<String, List<Map<String, Object>>> unitsByStockId) {
        Set<String> comboSeen = new HashSet<>();
        for (ExcelRow row : rows) {
            Map<String, Object> v = row.values();
            String stockId = str(v, "stockId");
            Object rawQty = v.get("qty");
            String qtyUnit = str(v, "qtyUnit");

            if (isBlank(stockId)) {
                errors.add(err(SHEET_UNITS, row, null, "stockId", "stockId is required"));
                continue;
            }
            if (!knownStockIds.contains(stockId)) {
                errors.add(err(SHEET_UNITS, row, stockId, "stockId",
                        "stockId \"" + stockId + "\" does not match any stockId in the Stocks sheet"));
            }

            ParsedQty parsedQty = parseQty(rawQty, qtyUnit);
            if (parsedQty == null) {
                errors.add(err(SHEET_UNITS, row, stockId, "qty",
                        "qty \"" + rawQty + "\" is not a valid positive number"));
            }
            if (isBlank(qtyUnit)) {
                errors.add(err(SHEET_UNITS, row, stockId, "qtyUnit", "qtyUnit is required"));
            }

            Double price = optionalNumber(SHEET_UNITS, row, stockId, "price", v.get("price"), errors);
            Double availableQty = optionalNumber(SHEET_UNITS, row, stockId, "availableQty", v.get("availableQty"), errors);

            if (parsedQty != null && !isBlank(qtyUnit)) {
                String comboKey = stockId + "|" + parsedQty.numericValue() + "|" + qtyUnit.trim().toLowerCase();
                if (!comboSeen.add(comboKey)) {
                    errors.add(err(SHEET_UNITS, row, stockId, "qty/qtyUnit",
                            "Duplicate stockId + qty + qtyUnit combination (" + parsedQty.formattedNumber() + " "
                                    + qtyUnit.trim() + ") within Stocks_units"));
                }
            }

            if (parsedQty == null) {
                // Nothing sensible to attach to unitsByStockId; the missing/invalid qty
                // was already recorded above, and the whole import fails anyway once
                // any error exists (documents are only built when errors is empty).
                continue;
            }

            Map<String, Object> unitDoc = new LinkedHashMap<>();
            unitDoc.put("qty", parsedQty.numericValue());
            unitDoc.put("qtyUnit", qtyUnit == null ? null : qtyUnit.trim());
            unitDoc.put("unit", parsedQty.formattedNumber() + (qtyUnit == null ? "" : qtyUnit.trim()));
            unitDoc.put("price", price);
            unitDoc.put("availableQty", availableQty);

            unitsByStockId.computeIfAbsent(stockId, k -> new ArrayList<>()).add(unitDoc);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Document builders (only ever invoked once the whole workbook is valid)
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildCategoryDoc(ExcelRow row) {
        Map<String, Object> v = row.values();
        Map<String, Object> name = nested(v);
        String status = str(v, "status");
        String nowIso = LocalDateTime.now().toString();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("categoryId", str(v, "categoryId"));
        doc.put("categoryName", str(v, "categoryName"));
        doc.put("name", Map.of("en", nullToEmpty(str(name, "en")), "ta", nullToEmpty(str(name, "ta"))));
        doc.put("description", str(v, "description"));
        doc.put("typeOfStore", str(v, "typeOfStore"));
        doc.put("status", status);
        // nkt.discover.categories filters on StaticCriteria {"active": true} — this bridges
        // the Excel's "status" column to that existing field so imported categories are
        // actually returned by the discover endpoint, without inventing a value the source
        // doesn't imply (active is a pure derivation of status).
        doc.put("active", "ACTIVE".equalsIgnoreCase(status));
        doc.put("icon", buildIndexedIconList(v, "icon[%d].filename", "icon[%d].localPath"));
        doc.put("updatedAt", nowIso);
        doc.put("createdAt", nowIso); // only applied on insert — see bulkUpsertByField's setOnInsertFields
        return doc;
    }

    private Map<String, Object> buildSubCategoryDoc(ExcelRow row) {
        Map<String, Object> v = row.values();
        Map<String, Object> name = nested(v);
        String nowIso = LocalDateTime.now().toString();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("subCategoryId", str(v, "subcategoryId"));
        doc.put("subCategoryName", str(v, "subCategoryName"));
        doc.put("name", Map.of("en", nullToEmpty(str(name, "en")), "ta", nullToEmpty(str(name, "ta"))));
        // subcategory.categoryId -> category.categoryId, per the source's parentCategoryId column
        doc.put("categoryId", str(v, "parentCategoryId"));
        doc.put("categoryName", str(v, "parentCategoryName"));
        doc.put("status", str(v, "status"));
        doc.put("icon", buildIndexedIconList(v, "icon[%d].filename", "icon[%d].localPath"));
        doc.put("updatedAt", nowIso);
        doc.put("createdAt", nowIso);
        return doc;
    }

    private Map<String, Object> buildStockDoc(ExcelRow row, List<Map<String, Object>> units) {
        Map<String, Object> v = row.values();
        Map<String, Object> name = nested(v);
        String nowIso = LocalDateTime.now().toString();

        List<String> imageFilenames = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String f = str(v, "image.filename[" + i + "]");
            if (f != null) imageFilenames.add(f);
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("stockId", str(v, "stockId"));
        doc.put("stockName", str(v, "stockName"));
        doc.put("name", Map.of("en", nullToEmpty(str(name, "en")), "ta", nullToEmpty(str(name, "ta"))));
        doc.put("storeId", str(v, "storeId"));
        doc.put("storeName", str(v, "storeName"));
        doc.put("categoryId", str(v, "categoryId"));
        doc.put("subCategoryId", str(v, "subCategoryId"));
        doc.put("nature", str(v, "nature"));
        doc.put("brand", str(v, "brand"));
        doc.put("status", str(v, "status"));
        doc.put("price", asNumber(v.get("price")));
        doc.put("discount", asNumber(v.get("discount")));
        doc.put("discountPercentage", asNumber(v.get("discountPercentage")));
        doc.put("finalPrice", asNumber(v.get("finalPrice")));
        doc.put("image", Map.of("filenames", imageFilenames, "localPath", nullToEmpty(str(v, "image.localPath"))));
        doc.put("unit", units);
        doc.put("updatedAt", nowIso);
        doc.put("createdAt", nowIso);
        return doc;
    }

    private List<Map<String, Object>> buildIndexedIconList(Map<String, Object> v, String filenamePattern, String pathPattern) {
        List<Map<String, Object>> icons = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String filename = str(v, String.format(filenamePattern, i));
            String path = str(v, String.format(pathPattern, i));
            if (filename == null && path == null) continue;
            Map<String, Object> icon = new LinkedHashMap<>();
            icon.put("filename", filename);
            icon.put("localPath", path);
            icons.add(icon);
        }
        return icons;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Small shared helpers
    // ─────────────────────────────────────────────────────────────────────

    private void validateStatus(String sheet, ExcelRow row, String entityId, String status, List<ExcelImportError> errors) {
        if (isBlank(status)) {
            errors.add(err(sheet, row, entityId, "status", "status is required"));
        } else if (!VALID_STATUSES.contains(status.trim().toUpperCase())) {
            errors.add(err(sheet, row, entityId, "status",
                    "status \"" + status + "\" is not one of " + VALID_STATUSES));
        }
    }

    private void requireNonNegativeNumber(String sheet, ExcelRow row, String entityId, String field,
                                           Object raw, List<ExcelImportError> errors, boolean optional) {
        if (raw == null) {
            if (!optional) errors.add(err(sheet, row, entityId, field, field + " is required"));
            return;
        }
        Double num = tryParseNumber(raw);
        if (num == null) {
            errors.add(err(sheet, row, entityId, field, field + " \"" + raw + "\" must be numeric"));
            return;
        }
        if (num < 0) {
            errors.add(err(sheet, row, entityId, field, field + " must not be negative"));
        }
        if (field.equals("discountPercentage") && num > 100) {
            errors.add(err(sheet, row, entityId, field, "discountPercentage must be between 0 and 100"));
        }
    }

    private Double optionalNumber(String sheet, ExcelRow row, String entityId, String field, Object raw,
                                   List<ExcelImportError> errors) {
        if (raw == null) return null;
        Double num = tryParseNumber(raw);
        if (num == null) {
            errors.add(err(sheet, row, entityId, field, field + " \"" + raw + "\" must be numeric"));
            return null;
        }
        if (num < 0) {
            errors.add(err(sheet, row, entityId, field, field + " must not be negative"));
        }
        return num;
    }

    private record ParsedQty(double numericValue, String formattedNumber) {
    }

    /**
     * Parses the Stocks_units "qty" cell into a number, tolerating a cell that
     * is already a combined "1Kg"-style string (never double-appends the unit
     * in that case) and rejecting genuinely non-numeric placeholders like "—".
     */
    private ParsedQty parseQty(Object raw, String qtyUnit) {
        if (raw == null) return null;
        if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (d <= 0) return null;
            return new ParsedQty(d, formatNumber(d));
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) return null;
        try {
            double d = Double.parseDouble(s);
            if (d <= 0) return null;
            return new ParsedQty(d, formatNumber(d));
        } catch (NumberFormatException ignored) {
            // fall through — maybe it's already "1Kg"-style
        }
        if (qtyUnit != null && !qtyUnit.isBlank() && s.toLowerCase().endsWith(qtyUnit.trim().toLowerCase())) {
            String numericPart = s.substring(0, s.length() - qtyUnit.trim().length()).trim();
            try {
                double d = Double.parseDouble(numericPart);
                if (d <= 0) return null;
                return new ParsedQty(d, formatNumber(d));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double tryParseNumber(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Object asNumber(Object raw) {
        Double d = tryParseNumber(raw);
        if (d == null) return null;
        return (d == Math.floor(d)) ? (Object) d.longValue() : (Object) d;
    }

    private String formatNumber(double d) {
        return BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> row) {
        // "name.en" / "name.ta" arrive as flat header keys, not a nested map — this
        // adapter lets the rest of the code read them as if they were nested.
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("en", row.get("name.en"));
        name.put("ta", row.get("name.ta"));
        return name;
    }

    private String str(Map<String, Object> v, String key) {
        Object o = v.get(key);
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private ExcelImportError err(String sheet, ExcelRow row, String stockId, String field, String message) {
        return ExcelImportError.of(sheet, row.rowNum(), stockId, field, message);
    }
}
