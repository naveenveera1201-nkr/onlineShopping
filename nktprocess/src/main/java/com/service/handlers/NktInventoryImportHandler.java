package com.service.handlers;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.models.excel.ExcelImportResult;
import com.service.ExcelStockImportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles {@code EXCEL_STOCK_MASTER_IMPORT} (process code
 * {@code nkt.inventory.excel_import}) — bulk import of Categories,
 * Sub_Categories, Stocks and Stocks_units from an uploaded .xlsx workbook
 * into the {@code categories} / {@code sub_categories} / {@code stocks}
 * collections.
 *
 * The file itself never comes through the normal JSON {@code data} param:
 * {@code ProcessEngineController.processUpload()} reads the multipart file
 * and folds its bytes into the same {@code data} map under {@code fileBytes}
 * / {@code fileName} before calling {@code NktCoreService.process()} —
 * so by the time this handler runs, the request has already been through
 * the same JWT auth, {@code allowedRoles} check ({@code business} only —
 * this project has no separate admin role) and {@code RequiredFields}
 * validation as every other {@code nkt.*} endpoint. This class only adds
 * the file-specific checks (present, non-empty, actually an Excel file) on
 * top of that.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NktInventoryImportHandler {

    private final ExcelStockImportService excelStockImportService;

    private String json(ObjectMapper m, Object o) {
        try {
            return m.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"error\":\"serialisation failed\"}";
        }
    }

    public NktOperationHandler importStockMaster() {
        return (data, userId, repo, mapper, def) -> {

            Object fileBytesObj = data.get("fileBytes");
            Object fileNameObj = data.get("fileName");

            if (!(fileBytesObj instanceof byte[] fileBytes) || fileBytes.length == 0) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "No file uploaded — send the workbook as multipart form field \"file\" to POST /data/upload",
                        "errorCode", "EXCEL_FILE_REQUIRED"
                ));
            }

            String fileName = fileNameObj == null ? "" : fileNameObj.toString();
            if (!fileName.isBlank()
                    && !fileName.toLowerCase().endsWith(".xlsx")
                    && !fileName.toLowerCase().endsWith(".xls")) {
                return json(mapper, Map.of(
                        "statusCode", "N400",
                        "statusDesc", "Unsupported file type \"" + fileName + "\" — expected an Excel .xlsx workbook",
                        "errorCode", "EXCEL_INVALID_FORMAT"
                ));
            }

            log.info("EXCEL_STOCK_MASTER_IMPORT: userId={}, fileName={}, size={} bytes", userId, fileName, fileBytes.length);

            ExcelImportResult result;
            try {
                result = excelStockImportService.importStockMaster(new ByteArrayInputStream(fileBytes));
            } catch (Exception e) {
                log.error("EXCEL_STOCK_MASTER_IMPORT failed for userId={}: {}", userId, e.getMessage(), e);
                return json(mapper, Map.of(
                        "statusCode", "N500",
                        "statusDesc", "Import failed: " + e.getMessage(),
                        "errorCode", "EXCEL_IMPORT_FAILED"
                ));
            }

            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("summary", result.getSummary());

            if (!result.isValid()) {
                responseData.put("errors", result.getErrors());
                return json(mapper, Map.of(
                        "data", responseData,
                        "statusCode", "N400",
                        "statusDesc", "Validation failed — nothing was written to the database. Fix the listed rows and re-upload.",
                        "errorCode", "EXCEL_VALIDATION_FAILED"
                ));
            }

            return json(mapper, Map.of(
                    "data", responseData,
                    "statusCode", "N200",
                    "statusDesc", "Stock master import completed successfully"
            ));
        };
    }
}
