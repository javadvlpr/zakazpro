package com.mebelsex.report;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Writes a {@link ReportTable} to an .xlsx workbook (Apache POI). No JavaFX here:
 * the controller chooses the file and runs this inside a background Task.
 */
public class ExcelExporter {

    /** Primary blue from AGENTS.md. */
    private static final byte[] HEADER_RGB = { (byte) 0x1E, (byte) 0x3A, (byte) 0x8A };
    private static final String MONEY_FORMAT = "#,##0";
    private static final String PERCENT_FORMAT = "0.0\"%\"";
    private static final String DATE_FORMAT = "dd.MM.yyyy";
    private static final String QUANTITY_FORMAT = "#,##0.###";

    public void export(ReportTable table, Path target) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Visible product identity in the workbook properties (file > Info in Excel).
            workbook.getProperties().getCoreProperties().setCreator(com.mebelsex.common.AppInfo.PRODUCT_NAME);
            workbook.getProperties().getCoreProperties().setTitle(table.title());
            XSSFSheet sheet = workbook.createSheet(sheetName(table.title()));

            XSSFCellStyle titleStyle = textStyle(workbook, true, 14);
            XSSFCellStyle periodStyle = textStyle(workbook, false, 11);
            XSSFCellStyle headerStyle = headerStyle(workbook);
            XSSFCellStyle textStyle = textStyle(workbook, false, 11);
            XSSFCellStyle boldTextStyle = textStyle(workbook, true, 11);
            XSSFCellStyle moneyStyle = numberStyle(workbook, MONEY_FORMAT, false);
            XSSFCellStyle boldMoneyStyle = numberStyle(workbook, MONEY_FORMAT, true);
            XSSFCellStyle integerStyle = numberStyle(workbook, MONEY_FORMAT, false);
            XSSFCellStyle boldIntegerStyle = numberStyle(workbook, MONEY_FORMAT, true);
            XSSFCellStyle percentStyle = numberStyle(workbook, PERCENT_FORMAT, false);
            XSSFCellStyle boldPercentStyle = numberStyle(workbook, PERCENT_FORMAT, true);
            XSSFCellStyle dateStyle = numberStyle(workbook, DATE_FORMAT, false);
            XSSFCellStyle quantityStyle = numberStyle(workbook, QUANTITY_FORMAT, false);

            int rowIndex = 0;
            XSSFRow titleRow = sheet.createRow(rowIndex++);
            setString(titleRow, 0, table.title(), titleStyle);
            XSSFRow periodRow = sheet.createRow(rowIndex++);
            setString(periodRow, 0, table.periodLabel(), periodStyle);

            int headerRowIndex = rowIndex++;
            XSSFRow headerRow = sheet.createRow(headerRowIndex);
            List<Column> columns = table.columns();
            for (int c = 0; c < columns.size(); c++) {
                XSSFCell cell = headerRow.createCell(c);
                cell.setCellValue(columns.get(c).title());
                cell.setCellStyle(headerStyle);
            }

            // Freeze the header row (and everything above it).
            sheet.createFreezePane(0, headerRowIndex + 1);
            if (!columns.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, headerRowIndex, 0, columns.size() - 1));
            }

            for (Row row : table.rows()) {
                writeRow(sheet.createRow(rowIndex++), row, columns, row.subtotal(),
                        textStyle, boldTextStyle, moneyStyle, boldMoneyStyle, integerStyle, boldIntegerStyle,
                        percentStyle, boldPercentStyle, dateStyle, quantityStyle);
            }
            if (table.totals() != null) {
                writeRow(sheet.createRow(rowIndex++), table.totals(), columns, true,
                        textStyle, boldTextStyle, moneyStyle, boldMoneyStyle, integerStyle, boldIntegerStyle,
                        percentStyle, boldPercentStyle, dateStyle, quantityStyle);
            }

            for (int c = 0; c < columns.size(); c++) {
                sheet.setColumnWidth(c, columnWidth(c, columns.get(c), table));
            }

            try (OutputStream out = Files.newOutputStream(target)) {
                workbook.write(out);
            }
        }
    }

    private void writeRow(XSSFRow excelRow, Row row, List<Column> columns, boolean bold,
            XSSFCellStyle textStyle, XSSFCellStyle boldTextStyle,
            XSSFCellStyle moneyStyle, XSSFCellStyle boldMoneyStyle,
            XSSFCellStyle integerStyle, XSSFCellStyle boldIntegerStyle,
            XSSFCellStyle percentStyle, XSSFCellStyle boldPercentStyle,
            XSSFCellStyle dateStyle, XSSFCellStyle quantityStyle) {
        List<Cell> cells = row.cells();
        for (int c = 0; c < columns.size(); c++) {
            CellType type = columns.get(c).type();
            XSSFCell cell = excelRow.createCell(c);
            Object value = c < cells.size() ? cells.get(c).value() : null;
            if (value == null) {
                cell.setCellValue("");
                cell.setCellStyle(bold ? boldTextStyle : textStyle);
                continue;
            }
            switch (type) {
                case INTEGER -> {
                    cell.setCellValue(((Number) value).doubleValue());
                    cell.setCellStyle(bold ? boldIntegerStyle : integerStyle);
                }
                case MONEY -> {
                    cell.setCellValue(((BigDecimal) value).doubleValue());
                    cell.setCellStyle(bold ? boldMoneyStyle : moneyStyle);
                }
                case PERCENT -> {
                    cell.setCellValue(((BigDecimal) value).doubleValue());
                    cell.setCellStyle(bold ? boldPercentStyle : percentStyle);
                }
                case QUANTITY -> {
                    cell.setCellValue(((BigDecimal) value).doubleValue());
                    cell.setCellStyle(quantityStyle);
                }
                case DATE -> {
                    cell.setCellValue(toExcelDate((LocalDate) value));
                    cell.setCellStyle(dateStyle);
                }
                case TEXT -> {
                    cell.setCellValue(String.valueOf(value));
                    cell.setCellStyle(bold ? boldTextStyle : textStyle);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    private XSSFCellStyle headerStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(HEADER_RGB));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle textStyle(XSSFWorkbook workbook, boolean bold, int fontSize) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) fontSize);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle numberStyle(XSSFWorkbook workbook, String format, boolean bold) {
        XSSFCellStyle style = workbook.createCellStyle();
        DataFormat dataFormat = workbook.createDataFormat();
        style.setDataFormat(dataFormat.getFormat(format));
        if (bold) {
            XSSFFont font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
        }
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private void setString(XSSFRow row, int column, String value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(column, org.apache.poi.ss.usermodel.CellType.STRING);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static double toExcelDate(LocalDate date) {
        Date utilDate = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        return org.apache.poi.ss.usermodel.DateUtil.getExcelDate(utilDate);
    }

    private int columnWidth(int index, Column column, ReportTable table) {
        int max = column.title() == null ? 8 : column.title().length();
        for (Row row : allRows(table)) {
            if (index < row.cells().size()) {
                max = Math.max(max, displayWidth(row.cells().get(index).value()));
            }
        }
        int width = (int) ((max + 2) * 256);
        return Math.min(Math.max(width, 10 * 256), 60 * 256);
    }

    private List<Row> allRows(ReportTable table) {
        List<Row> rows = new java.util.ArrayList<>(table.rows());
        if (table.totals() != null) {
            rows.add(table.totals());
        }
        return rows;
    }

    private int displayWidth(Object value) {
        if (value == null) {
            return 1;
        }
        if (value instanceof BigDecimal bd) {
            return bd.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString().length();
        }
        return String.valueOf(value).length();
    }

    /** Excel-safe sheet name: strips invalid characters and caps the length at 31. */
    static String sheetName(String title) {
        if (title == null) {
            return "Hisobot";
        }
        String clean = title.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (clean.isEmpty()) {
            clean = "Hisobot";
        }
        return clean.length() > 31 ? clean.substring(0, 31) : clean;
    }
}
