package com.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.service.ExcelReaderUtil.ExcelRow;

class ExcelReaderUtilTest {

    private final ExcelReaderUtil reader = new ExcelReaderUtil();

    private Workbook openBytes(byte[] bytes) throws Exception {
        return reader.open(new ByteArrayInputStream(bytes));
    }

    private byte[] toBytes(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    @Test
    void readSheet_returnsRowsKeyedByHeader() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Categories");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("categoryId");
        header.createCell(1).setCellValue("categoryName");

        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("CAT_001");
        r1.createCell(1).setCellValue("Rice & Staples");

        Workbook opened = openBytes(toBytes(wb));
        List<ExcelRow> rows = reader.readSheet(opened, "Categories");

        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).rowNum()); // header is row 1, first data row is row 2
        assertEquals("CAT_001", rows.get(0).values().get("categoryId"));
        assertEquals("Rice & Staples", rows.get(0).values().get("categoryName"));
        opened.close();
    }

    @Test
    void readSheet_missingSheet_returnsNull() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        wb.createSheet("SomeOtherSheet");
        Workbook opened = openBytes(toBytes(wb));

        assertNull(reader.readSheet(opened, "Categories"));
        opened.close();
    }

    @Test
    void readSheet_skipsBlankHeaderColumnsAndBlankTrailingRows() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Stocks_units");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("stockId");
        header.createCell(1).setCellValue("qty");
        // header.createCell(2) intentionally left blank, mirrors the real file's
        // two trailing blank columns after "availableQty"

        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("ST011_PROD001");
        r1.createCell(1).setCellValue(1);

        // fully blank trailing row — must be skipped, not returned as a phantom row
        sheet.createRow(2);

        Workbook opened = openBytes(toBytes(wb));
        List<ExcelRow> rows = reader.readSheet(opened, "Stocks_units");

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).values().containsKey("stockId"));
        assertEquals(2, rows.get(0).values().size()); // blank header column never became a key
        opened.close();
    }

    @Test
    void readSheet_numericCell_returnsWholeNumberAsLong() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Stocks");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("price");
        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue(50);

        Workbook opened = openBytes(toBytes(wb));
        Map<String, Object> row = reader.readSheet(opened, "Stocks").get(0).values();

        assertEquals(50L, row.get("price"));
        opened.close();
    }
}
