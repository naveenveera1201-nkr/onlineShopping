package com.models.excel;

/**
 * One structured validation error surfaced by the Excel stock-master import
 * ({@code EXCEL_STOCK_MASTER_IMPORT}).
 *
 * Mirrors the shape requested in the response contract:
 * {@code {"sheet":"Stocks","row":12,"stockId":"ST011_PROD012","field":"price","message":"..."}}
 *
 * {@code row} is the 1-based Excel row number (header row = 1, so the first
 * data row is 2) so the user can jump straight to the offending cell.
 */
public record ExcelImportError(String sheet, int row, String stockId, String field, String message) {

    public static ExcelImportError of(String sheet, int row, String stockId, String field, String message) {
        return new ExcelImportError(sheet, row, stockId, field, message);
    }
}
