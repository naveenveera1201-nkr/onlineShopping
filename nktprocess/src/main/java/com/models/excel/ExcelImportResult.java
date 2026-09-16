package com.models.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

/**
 * Outcome of one {@code EXCEL_STOCK_MASTER_IMPORT} run.
 *
 * Two possible shapes, matching the response contract:
 * <ul>
 *   <li><b>Validation failure</b> — {@link #valid} is {@code false}, {@link #errors}
 *       is non-empty, nothing was written to MongoDB (READ ALL → VALIDATE ALL →
 *       IF VALID → WRITE; no partial writes).</li>
 *   <li><b>Success</b> — {@link #valid} is {@code true}, {@link #errors} is empty,
 *       {@link #summary} carries insert/update counts per collection.</li>
 * </ul>
 */
@Getter
public class ExcelImportResult {

    private boolean valid;
    private final List<ExcelImportError> errors = new ArrayList<>();
    private final Map<String, Object> summary = new LinkedHashMap<>();

    public static ExcelImportResult failure(List<ExcelImportError> errors, Map<String, Object> summary) {
        ExcelImportResult r = new ExcelImportResult();
        r.valid = false;
        r.errors.addAll(errors);
        r.summary.putAll(summary);
        return r;
    }

    public static ExcelImportResult success(Map<String, Object> summary) {
        ExcelImportResult r = new ExcelImportResult();
        r.valid = true;
        r.summary.putAll(summary);
        return r;
    }

    public void addError(ExcelImportError error) {
        this.errors.add(error);
    }

    public void addErrors(List<ExcelImportError> more) {
        this.errors.addAll(more);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
