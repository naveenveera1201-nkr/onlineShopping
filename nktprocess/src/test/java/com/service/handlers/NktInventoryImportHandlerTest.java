package com.service.handlers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.excel.ExcelImportError;
import com.models.excel.ExcelImportResult;
import com.models.nkt.NktProcessDefinition;
import com.repository.NktDynamicRepository;
import com.service.ExcelStockImportService;

/**
 * {@code NktCoreService} already handles JWT auth, the {@code business}-only
 * {@code allowedRoles} gate and generic {@code RequiredFields} before this
 * handler is ever invoked (declarative in process-flow.json, same as every
 * other {@code nkt.*} endpoint) — so these tests focus on what only this
 * handler does: pulling {@code fileBytes}/{@code fileName} back out of
 * {@code data} and shaping {@link ExcelStockImportService}'s result into the
 * project's {@code statusCode}/{@code statusDesc}/{@code errorCode} response.
 */
@ExtendWith(MockitoExtension.class)
class NktInventoryImportHandlerTest {

    @Mock
    private NktDynamicRepository repo;

    @Mock
    private ExcelStockImportService excelStockImportService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final NktProcessDefinition def = new NktProcessDefinition();

    private NktInventoryImportHandler handler() {
        return new NktInventoryImportHandler(excelStockImportService);
    }

    @Test
    void missingFile_returnsFileRequiredWithoutCallingService() {
        Map<String, Object> data = new LinkedHashMap<>();
        // no "fileBytes" key at all — matches a request sent through /data instead of /data/upload

        String response = handler().importStockMaster().handle(data, "OWNER_A", repo, mapper, def);

        assertTrue(response.contains("EXCEL_FILE_REQUIRED"));
        assertTrue(response.contains("\"N400\""));
        verify(excelStockImportService, never()).importStockMaster(any());
    }

    @Test
    void emptyFileBytes_returnsFileRequired() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileBytes", new byte[0]);
        data.put("fileName", "Store_maintenance.xlsx");

        String response = handler().importStockMaster().handle(data, "OWNER_A", repo, mapper, def);

        assertTrue(response.contains("EXCEL_FILE_REQUIRED"));
        verify(excelStockImportService, never()).importStockMaster(any());
    }

    @Test
    void wrongFileExtension_returnsInvalidFormat() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileBytes", new byte[]{1, 2, 3});
        data.put("fileName", "not_an_excel_file.pdf");

        String response = handler().importStockMaster().handle(data, "OWNER_A", repo, mapper, def);

        assertTrue(response.contains("EXCEL_INVALID_FORMAT"));
        assertTrue(response.contains("\"N400\""));
        verify(excelStockImportService, never()).importStockMaster(any());
    }

    @Test
    void validationFailure_returnsN400WithErrorsArray() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileBytes", new byte[]{1, 2, 3});
        data.put("fileName", "Store_maintenance.xlsx");

        ExcelImportResult failure = ExcelImportResult.failure(
                List.of(ExcelImportError.of("Stocks", 5, "ST011_PROD005", "categoryId", "categoryId is required")),
                Map.of("stocksRows", 2734, "totalErrors", 1));
        when(excelStockImportService.importStockMaster(any(InputStream.class))).thenReturn(failure);

        String response = handler().importStockMaster().handle(data, "OWNER_A", repo, mapper, def);

        assertTrue(response.contains("\"N400\""));
        assertTrue(response.contains("EXCEL_VALIDATION_FAILED"));
        assertTrue(response.contains("categoryId is required"));
        assertTrue(response.contains("\"errors\""));
    }

    @Test
    void successfulImport_returnsN200WithSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileBytes", new byte[]{1, 2, 3});
        data.put("fileName", "Store_maintenance.xlsx");

        ExcelImportResult success = ExcelImportResult.success(Map.of(
                "categoriesInserted", 26L, "stocksInserted", 2734L, "totalUnitsImported", 3953L));
        when(excelStockImportService.importStockMaster(any(InputStream.class))).thenReturn(success);

        String response = handler().importStockMaster().handle(data, "OWNER_A", repo, mapper, def);

        assertTrue(response.contains("\"N200\""));
        assertTrue(response.contains("\"summary\""));
        assertTrue(response.contains("2734"));
    }
}
