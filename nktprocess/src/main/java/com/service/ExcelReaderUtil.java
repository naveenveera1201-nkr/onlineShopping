package com.service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import org.springframework.stereotype.Component;

/**
 * Thin, generic Apache POI wrapper for the Excel stock-master import.
 *
 * Reads exactly the sheets it is asked for (the caller — {@link ExcelStockImportService})
 * decides which of the workbook's sheets are relevant; any other sheet in the
 * uploaded file is simply never touched, satisfying "read only Categories,
 * Sub_Categories, Stocks, Stocks_units — ignore any other sheets".
 *
 * No business logic lives here: this class only turns spreadsheet cells into
 * plain {@code Map<String,Object>} rows keyed by the sheet's own header row,
 * so the actual header names in the file drive everything downstream.
 */
@Component
public class ExcelReaderUtil {

    /** One data row, keeping its 1-based Excel row number for error reporting. */
    public record ExcelRow(int rowNum, Map<String, Object> values) {
    }

    public Workbook open(InputStream in) throws Exception {
        return WorkbookFactory.create(in);
    }

    /**
     * @return {@code null} if the workbook has no sheet with that exact name
     *         (the caller turns that into a "missing sheet" validation error).
     */
    public List<ExcelRow> readSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            return null;
        }

        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        int firstRowNum = sheet.getFirstRowNum();
        Row headerRow = sheet.getRow(firstRowNum);
        if (headerRow == null) {
            return new ArrayList<>();
        }

        // Header cell index -> header name (blank/null header cells are skipped,
        // e.g. the two trailing blank columns after "availableQty" in Stocks_units).
        Map<Integer, String> headerByIndex = new LinkedHashMap<>();
        for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            String header = cellToString(cell, evaluator);
            if (header != null && !header.isBlank()) {
                headerByIndex.put(c, header.trim());
            }
        }

        List<ExcelRow> rows = new ArrayList<>();
        for (int r = firstRowNum + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isEntirelyBlank(row, headerByIndex.keySet(), evaluator)) {
                continue; // skip fully blank trailing rows
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : headerByIndex.entrySet()) {
                Cell cell = row.getCell(e.getKey());
                values.put(e.getValue(), cellValue(cell, evaluator));
            }
            // Excel rows are 0-based internally; +1 gives the row number as the
            // user would see it in Excel (header = row 1, first data row = row 2).
            rows.add(new ExcelRow(r + 1, values));
        }
        return rows;
    }

    private boolean isEntirelyBlank(Row row, Iterable<Integer> columns, FormulaEvaluator evaluator) {
        for (int c : columns) {
            Object v = cellValue(row.getCell(c), evaluator);
            if (v != null && !(v instanceof String s && s.isBlank())) {
                return false;
            }
        }
        return true;
    }

    /** Extracts a cell's value as String / Double / Boolean / LocalDateTime / null. */
    public Object cellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = evaluator.evaluateFormulaCell(cell) != null
                    ? cell.getCachedFormulaResultType()
                    : CellType.BLANK;
        }
        switch (type) {
            case STRING -> {
                String s = cell.getStringCellValue();
                return (s == null || s.isBlank()) ? null : s.trim();
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue();
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1_000_000_000_000L) {
                    return (long) d;
                }
                return d;
            }
            case BOOLEAN -> {
                return cell.getBooleanCellValue();
            }
            case BLANK, _NONE -> {
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private String cellToString(Cell cell, FormulaEvaluator evaluator) {
        Object v = cellValue(cell, evaluator);
        return v == null ? null : v.toString();
    }

    /** Converts an Excel numeric/date value already resolved above to a LocalDateTime, if any. */
    public LocalDateTime asDateTime(Object value) {
        return value instanceof LocalDateTime ldt ? ldt : null;
    }
}
