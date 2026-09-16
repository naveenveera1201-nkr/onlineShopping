package com.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.models.excel.ExcelImportError;
import com.models.excel.ExcelImportResult;
import com.mongodb.bulk.BulkWriteResult;

/**
 * Exercises {@link ExcelStockImportService} against real, small, in-memory
 * .xlsx workbooks built with Apache POI — the same library the production
 * code reads with — rather than hand-rolled row maps, so header-name
 * mismatches or POI cell-type quirks would actually surface here.
 *
 * {@link MongoBulkUpsertService} is mocked: these are unit tests of the
 * read → validate → build pipeline, not of MongoDB itself.
 */
@ExtendWith(MockitoExtension.class)
class ExcelStockImportServiceTest {

    private static final String[] CATEGORY_HEADERS =
            {"categoryId", "categoryName", "name.en", "name.ta", "description", "typeOfStore", "status"};
    private static final String[] SUBCATEGORY_HEADERS =
            {"subcategoryId", "subCategoryName", "name.en", "name.ta", "parentCategoryId", "status", "parentCategoryName"};
    private static final String[] STOCK_HEADERS =
            {"stockId", "stockName", "name.en", "name.ta", "storeId", "categoryId", "subCategoryId",
                    "nature", "brand", "status", "price", "discount", "discountPercentage", "finalPrice"};
    private static final String[] UNIT_HEADERS =
            {"stockId", "qty", "qtyUnit", "price", "availableQty"};

    @Mock
    private MongoBulkUpsertService bulkUpsertService;

    private ExcelStockImportService service() {
        return new ExcelStockImportService(new ExcelReaderUtil(), bulkUpsertService);
    }

    // ── workbook builders ───────────────────────────────────────────────

    private static Map<String, Object> category(String id, String name, String typeOfStore, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categoryId", id);
        m.put("categoryName", name);
        m.put("name.en", name);
        m.put("name.ta", name);
        m.put("description", name);
        m.put("typeOfStore", typeOfStore);
        m.put("status", status);
        return m;
    }

    private static Map<String, Object> subCategory(String id, String name, String parentCategoryId, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subcategoryId", id);
        m.put("subCategoryName", name);
        m.put("name.en", name);
        m.put("name.ta", name);
        m.put("parentCategoryId", parentCategoryId);
        m.put("status", status);
        m.put("parentCategoryName", "ParentOf" + id);
        return m;
    }

    private static Map<String, Object> stock(String id, String name, String storeId, String categoryId,
                                              String subCategoryId, String status, Number price) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stockId", id);
        m.put("stockName", name);
        m.put("name.en", name);
        m.put("name.ta", name);
        m.put("storeId", storeId);
        m.put("categoryId", categoryId);
        m.put("subCategoryId", subCategoryId);
        m.put("nature", "packed");
        m.put("brand", "Store");
        m.put("status", status);
        m.put("price", price);
        m.put("discount", 0);
        m.put("discountPercentage", 0);
        m.put("finalPrice", price);
        return m;
    }

    private static Map<String, Object> unit(String stockId, Object qty, String qtyUnit, Object price, Object availableQty) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stockId", stockId);
        m.put("qty", qty);
        m.put("qtyUnit", qtyUnit);
        m.put("price", price);
        m.put("availableQty", availableQty);
        return m;
    }

    private InputStream workbook(List<Map<String, Object>> categories, List<Map<String, Object>> subCategories,
                                  List<Map<String, Object>> stocks, List<Map<String, Object>> units) throws Exception {
        return workbook(categories, subCategories, stocks, units, true, true, true, true);
    }

    private InputStream workbook(List<Map<String, Object>> categories, List<Map<String, Object>> subCategories,
                                  List<Map<String, Object>> stocks, List<Map<String, Object>> units,
                                  boolean includeCategories, boolean includeSubCategories,
                                  boolean includeStocks, boolean includeUnits) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            if (includeCategories) addSheet(wb, "Categories", CATEGORY_HEADERS, categories);
            if (includeSubCategories) addSheet(wb, "Sub_Categories", SUBCATEGORY_HEADERS, subCategories);
            if (includeStocks) addSheet(wb, "Stocks", STOCK_HEADERS, stocks);
            if (includeUnits) addSheet(wb, "Stocks_units", UNIT_HEADERS, units);
            wb.createSheet("SomeIrrelevantSheet"); // must always be ignored

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private void addSheet(XSSFWorkbook wb, String name, String[] headers, List<Map<String, Object>> rows) {
        Sheet sheet = wb.createSheet(name);
        Row header = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) header.createCell(c).setCellValue(headers[c]);

        int r = 1;
        for (Map<String, Object> row : rows) {
            Row excelRow = sheet.createRow(r++);
            for (int c = 0; c < headers.length; c++) {
                Object v = row.get(headers[c]);
                if (v == null) continue;
                if (v instanceof Number n) excelRow.createCell(c).setCellValue(n.doubleValue());
                else excelRow.createCell(c).setCellValue(v.toString());
            }
        }
    }

    private List<Map<String, Object>> oneValidCategory() {
        return List.of(category("CAT_001", "Rice & Staples", "Supermarket", "ACTIVE"));
    }

    private List<Map<String, Object>> oneValidSubCategory() {
        return List.of(subCategory("SUBCAT_001", "Raw Rice", "CAT_001", "ACTIVE"));
    }

    private List<Map<String, Object>> twoValidStocks() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(stock("ST011_PROD001", "Raw rice", "ST011", "CAT_001", "SUBCAT_001", "ACTIVE", 50));
        list.add(stock("ST011_PROD002", "Broken rice", "ST011", "CAT_001", "SUBCAT_001", "ACTIVE", 40));
        return list;
    }

    private List<Map<String, Object>> unitsForTwoStocks() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(unit("ST011_PROD001", 1, "Kg", 45, 10));
        list.add(unit("ST011_PROD001", 26, "Kg", 45, 10));
        list.add(unit("ST011_PROD002", 1, "Kg", 35, 5));
        return list;
    }

    private void mockSuccessfulWrite() {
        when(bulkUpsertService.upsertImport(anyList(), anyList(), anyList()))
                .thenReturn(Map.of());
    }

    // ── Test 1: valid import ────────────────────────────────────────────

    @Test
    void validImport_writesAndReturnsCorrectSummary() throws Exception {
        mockSuccessfulWrite();
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), unitsForTwoStocks());

        ExcelImportResult result = service().importStockMaster(in);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertEquals(1, result.getSummary().get("categoriesRows"));
        assertEquals(2, result.getSummary().get("stocksRows"));
        assertEquals(3L, result.getSummary().get("totalUnitsImported"));

        ArgumentCaptor<List<Map<String, Object>>> stockDocsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bulkUpsertService).upsertImport(anyList(), anyList(), stockDocsCaptor.capture());

        Map<String, Object> stock1 = stockDocsCaptor.getValue().stream()
                .filter(d -> "ST011_PROD001".equals(d.get("stockId"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> units = (List<Map<String, Object>>) stock1.get("unit");
        assertEquals(2, units.size());
        assertEquals("1Kg", units.get(0).get("unit"));
        assertEquals("26Kg", units.get(1).get("unit"));
        // NktOrderHandler reads exactly these three keys off each unit map — must survive the import untouched.
        assertEquals(45.0, units.get(0).get("price"));
        assertEquals(10.0, units.get(0).get("availableQty"));
    }

    // ── Test 2-5: each required sheet missing ───────────────────────────

    @Test
    void missingCategoriesSheet_failsWithSheetError() throws Exception {
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), unitsForTwoStocks(),
                false, true, true, true);

        ExcelImportResult result = service().importStockMaster(in);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.sheet().equals("Categories") && e.field().equals("sheet")));
        verify(bulkUpsertService, never()).upsertImport(anyList(), anyList(), anyList());
    }

    @Test
    void missingSubCategoriesSheet_failsWithSheetError() throws Exception {
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), unitsForTwoStocks(),
                true, false, true, true);
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.sheet().equals("Sub_Categories")));
    }

    @Test
    void missingStocksSheet_failsWithSheetError() throws Exception {
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), unitsForTwoStocks(),
                true, true, false, true);
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.sheet().equals("Stocks")));
    }

    @Test
    void missingStocksUnitsSheet_failsWithSheetError() throws Exception {
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), unitsForTwoStocks(),
                true, true, true, false);
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.sheet().equals("Stocks_units")));
    }

    // ── Test 6: empty workbook (all 4 sheets present, no data rows) ────

    @Test
    void emptyWorkbook_failsWithNoDataError() throws Exception {
        InputStream in = workbook(List.of(), List.of(), List.of(), List.of());
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        verify(bulkUpsertService, never()).upsertImport(anyList(), anyList(), anyList());
    }

    // ── Test 7: extra irrelevant sheet never breaks a valid import (already
    //            exercised implicitly by every other test via workbook(), which
    //            always adds "SomeIrrelevantSheet") — asserted explicitly here.

    @Test
    void extraSheet_isIgnored() throws Exception {
        mockSuccessfulWrite();
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), unitsForTwoStocks());
        ExcelImportResult result = service().importStockMaster(in);
        assertTrue(result.isValid());
    }

    // ── Test 8-10: duplicate business keys within a sheet ───────────────

    @Test
    void duplicateCategoryId_isRejected() throws Exception {
        List<Map<String, Object>> cats = List.of(
                category("CAT_001", "Rice", "Supermarket", "ACTIVE"),
                category("CAT_001", "Rice Again", "Supermarket", "ACTIVE"));
        InputStream in = workbook(cats, oneValidSubCategory(), twoValidStocks(), unitsForTwoStocks());
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("Duplicate categoryId")));
    }

    @Test
    void duplicateSubCategoryId_isRejected() throws Exception {
        List<Map<String, Object>> subs = List.of(
                subCategory("SUBCAT_001", "Raw Rice", "CAT_001", "ACTIVE"),
                subCategory("SUBCAT_001", "Raw Rice 2", "CAT_001", "ACTIVE"));
        InputStream in = workbook(oneValidCategory(), subs, twoValidStocks(), unitsForTwoStocks());
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("Duplicate subcategoryId")));
    }

    @Test
    void duplicateStockId_isRejected() throws Exception {
        List<Map<String, Object>> stocks = List.of(
                stock("ST011_PROD001", "Raw rice", "ST011", "CAT_001", "SUBCAT_001", "ACTIVE", 50),
                stock("ST011_PROD001", "Raw rice dup", "ST011", "CAT_001", "SUBCAT_001", "ACTIVE", 55));
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), stocks, unitsForTwoStocks());
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("Duplicate stockId")));
    }

    // ── Test 11: unknown stockId referenced in Stocks_units ─────────────

    @Test
    void unknownStockIdInUnits_isRejected() throws Exception {
        List<Map<String, Object>> units = List.of(unit("ST011_PROD_GHOST", 1, "Kg", 45, 10));
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), units);
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.field().equals("stockId")
                && e.message().contains("does not match any stockId")));
    }

    // ── Test 12: duplicate (stockId, qty, qtyUnit) in Stocks_units ──────

    @Test
    void duplicateUnitCombo_isRejected() throws Exception {
        List<Map<String, Object>> units = List.of(
                unit("ST011_PROD001", 1, "Kg", 45, 10),
                unit("ST011_PROD001", 1, "Kg", 45, 10));
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), units);
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("Duplicate stockId + qty + qtyUnit")));
    }

    // ── Test 13: invalid price ───────────────────────────────────────────

    @Test
    void invalidStockPrice_isRejected() throws Exception {
        List<Map<String, Object>> stocks = List.of(
                stock("ST011_PROD001", "Raw rice", "ST011", "CAT_001", "SUBCAT_001", "ACTIVE", -10));
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), stocks, unitsForTwoStocks());
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.field().equals("price")));
    }

    // ── Test 14: invalid availableQty (non-numeric) in Stocks_units ────

    @Test
    void invalidAvailableQty_isRejected() throws Exception {
        List<Map<String, Object>> units = List.of(unit("ST011_PROD001", 1, "Kg", 45, "not-a-number"));
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), units);
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.field().equals("availableQty")));
    }

    // ── Test 15: stock with zero units is allowed (empty unit[], not an error) ──

    @Test
    void stockWithZeroUnits_isAllowed() throws Exception {
        mockSuccessfulWrite();
        List<Map<String, Object>> stocks = List.of(
                stock("ST011_PROD001", "Raw rice", "ST011", "CAT_001", "SUBCAT_001", "ACTIVE", 50));
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), stocks, List.of());

        ExcelImportResult result = service().importStockMaster(in);

        assertTrue(result.isValid());
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkUpsertService).upsertImport(anyList(), anyList(), captor.capture());
        assertTrue(((List<?>) captor.getValue().get(0).get("unit")).isEmpty());
    }

    // ── Test 16: referential integrity — stock referencing unknown categoryId/subCategoryId ──

    @Test
    void stockWithUnknownCategoryId_isRejected() throws Exception {
        List<Map<String, Object>> stocks = List.of(
                stock("ST011_PROD001", "Raw rice", "ST011", "CAT_999", "SUBCAT_001", "ACTIVE", 50));
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), stocks, List.of());
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.field().equals("categoryId")
                && e.message().contains("does not match any categoryId")));
    }

    @Test
    void subCategoryWithUnknownParentCategoryId_isRejected() throws Exception {
        List<Map<String, Object>> subs = List.of(subCategory("SUBCAT_001", "Raw Rice", "CAT_999", "ACTIVE"));
        InputStream in = workbook(oneValidCategory(), subs, List.of(), List.of());
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.field().equals("parentCategoryId")));
    }

    // ── Test 17: qty already combined with its unit ("1Kg") is not double-appended ──

    @Test
    void alreadyCombinedQtyString_isNotDoubleAppended() throws Exception {
        mockSuccessfulWrite();
        Map<String, Object> combinedUnit = unit("ST011_PROD001", null, "Kg", 45, 10);
        combinedUnit.put("qty", "1Kg"); // simulate a source cell that already has the unit baked in
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), List.of(combinedUnit));

        ExcelImportResult result = service().importStockMaster(in);

        assertTrue(result.isValid());
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkUpsertService).upsertImport(anyList(), anyList(), captor.capture());
        Map<String, Object> stock1 = captor.getValue().stream()
                .filter(d -> "ST011_PROD001".equals(d.get("stockId"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> units = (List<Map<String, Object>>) stock1.get("unit");
        assertEquals("1Kg", units.get(0).get("unit")); // NOT "1KgKg"
    }

    // ── Test 18: non-numeric qty placeholder ("—") is rejected, not silently dropped ──

    @Test
    void nonNumericQtyPlaceholder_isRejected() throws Exception {
        List<Map<String, Object>> units = List.of(unit("ST011_PROD001", "—", "—", null, 10));
        InputStream in = workbook(oneValidCategory(), oneValidSubCategory(), twoValidStocks(), units);
        ExcelImportResult result = service().importStockMaster(in);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.field().equals("qty")));
    }

    // ── Test 19: constructor error record shape matches the documented contract ──

    @Test
    void errorShape_matchesDocumentedContract() throws Exception {
        List<Map<String, Object>> cats = List.of(category(null, "Rice", "Supermarket", "ACTIVE"));
        InputStream in = workbook(cats, List.of(), List.of(), List.of());
        ExcelImportResult result = service().importStockMaster(in);

        assertFalse(result.isValid());
        ExcelImportError anyError = result.getErrors().get(0);
        // sheet / row / stockId / field / message — exactly the fields the response contract promises.
        assertEquals("Categories", anyError.sheet());
        assertEquals(2, anyError.row()); // header = row 1, first data row = row 2
        assertEquals("categoryId", anyError.field());
        assertTrue(anyError.message() != null && !anyError.message().isBlank());
    }
}
