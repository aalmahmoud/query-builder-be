package querydsl.export;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Generic Excel exporter that works with any data structure
 */
public class ExcelExporter {

    /**
     * Export data to Excel format
     * 
     * @param data List of maps representing rows (column name -> value)
     * @param friendlyHeaders Optional map of column names to friendly header names
     * @return Byte array containing the Excel file
     */
    public static byte[] export(List<Map<String, Object>> data, Map<String, String> friendlyHeaders) {
        if (data == null || data.isEmpty()) {
            return new byte[0];
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            CreationHelper creationHelper = workbook.getCreationHelper();

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Header Row
            Row header = sheet.createRow(0);
            int colIndex = 0;
            Map<String, Object> firstRow = data.get(0);
            
            for (String key : firstRow.keySet()) {
                String headerText = friendlyHeaders != null
                        ? friendlyHeaders.getOrDefault(key, key)
                        : key;
                Cell cell = header.createCell(colIndex++);
                cell.setCellValue(headerText);
                cell.setCellStyle(headerStyle);
            }

            // Data Rows
            int rowIndex = 1;
            for (Map<String, Object> rowMap : data) {
                Row row = sheet.createRow(rowIndex++);
                int cellIndex = 0;
                for (String key : firstRow.keySet()) {
                    Object value = rowMap.get(key);
                    Cell cell = row.createCell(cellIndex++);
                    
                    if (value != null) {
                        if (value instanceof Number) {
                            cell.setCellValue(((Number) value).doubleValue());
                        } else if (value instanceof Boolean) {
                            cell.setCellValue((Boolean) value);
                        } else {
                            cell.setCellValue(value.toString());
                        }
                    } else {
                        cell.setCellValue("");
                    }
                }
            }

            // Phase 5 fix 5.18: autoSizeColumn iterates every row computing widths and is
            // dramatically slow on large exports (10s of seconds for 10k+ rows). Skip it
            // beyond a sensible threshold and apply a fixed default width instead.
            final int AUTO_SIZE_THRESHOLD = 5_000;
            if (data.size() <= AUTO_SIZE_THRESHOLD) {
                for (int i = 0; i < firstRow.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            } else {
                for (int i = 0; i < firstRow.size(); i++) {
                    sheet.setColumnWidth(i, 20 * 256); // ~20 chars wide
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to export Excel file", e);
        }
    }
}
