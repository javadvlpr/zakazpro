package com.mebelsex.report;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelExporterTest {

    private ReportTable sampleTable() {
        List<Column> columns = List.of(
                Column.of("Zakaz", CellType.TEXT, Align.LEFT),
                Column.of("Narx", CellType.MONEY, Align.RIGHT));
        List<Row> rows = List.of(
                Row.data(List.of(Cell.of("1"), Cell.of(new BigDecimal("100000"))), 7L),
                Row.data(List.of(Cell.of("2"), Cell.of(new BigDecimal("50000"))), 8L));
        Row totals = Row.subtotal(List.of(Cell.of("Jami"), Cell.of(new BigDecimal("150000"))));
        return new ReportTable("Davr hisoboti", "01.01.2026 \u2013 31.01.2026", List.of(), columns, rows, totals);
    }

    @Test
    void writesHeaderDataAndTotalsRow() throws Exception {
        Path file = Files.createTempFile("report-", ".xlsx");
        new ExcelExporter().export(sampleTable(), file);

        assertTrue(Files.size(file) > 0, "exported file must not be empty");

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            assertEquals(1, workbook.getNumberOfSheets());
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals(ExcelExporter.sheetName("Davr hisoboti"), sheet.getSheetName());

            assertEquals("Davr hisoboti", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("01.01.2026 \u2013 31.01.2026", sheet.getRow(1).getCell(0).getStringCellValue());

            XSSFRow header = sheet.getRow(2);
            assertEquals("Zakaz", header.getCell(0).getStringCellValue());
            assertEquals("Narx", header.getCell(1).getStringCellValue());
            assertTrue(header.getCell(0).getCellStyle().getFont().getBold());

            XSSFRow data = sheet.getRow(3);
            assertEquals(org.apache.poi.ss.usermodel.CellType.STRING, data.getCell(0).getCellType());
            assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC, data.getCell(1).getCellType());
            assertEquals(100000d, data.getCell(1).getNumericCellValue(), 0.001);

            XSSFRow totals = sheet.getRow(5);
            assertEquals("Jami", totals.getCell(0).getStringCellValue());
            assertTrue(totals.getCell(1).getCellStyle().getFont().getBold(), "totals row must be bold");
            assertEquals(150000d, totals.getCell(1).getNumericCellValue(), 0.001);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void sheetNameIsSafeAndShort() {
        assertEquals("Davr hisoboti", ExcelExporter.sheetName("Davr hisoboti"));
        String unsafe = ExcelExporter.sheetName("A/B:C*D?E[F]:G");
        assertFalse(unsafe.matches(".*[\\\\/?*\\[\\]:].*"), "invalid Excel characters must be removed: " + unsafe);
        assertTrue(unsafe.length() <= 31);

        String longName = "X".repeat(40);
        assertEquals(31, ExcelExporter.sheetName(longName).length());
        assertEquals("Hisobot", ExcelExporter.sheetName(null));
    }
}
