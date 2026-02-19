package querydsl.export;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Generic PDF exporter that works with any data structure
 */
public class PdfExporter {

    /**
     * Export data to PDF format
     * 
     * @param data List of maps representing rows (column name -> value)
     * @param friendlyHeaders Optional map of column names to friendly header names
     * @return Byte array containing the PDF file
     */
    public static byte[] export(List<Map<String, Object>> data, Map<String, String> friendlyHeaders) {
        if (data == null || data.isEmpty()) {
            return new byte[0];
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            Map<String, Object> firstRow = data.get(0);
            PdfPTable table = new PdfPTable(firstRow.size());
            table.setWidthPercentage(100);

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            // Header Row
            for (String key : firstRow.keySet()) {
                String headerText = friendlyHeaders != null
                        ? friendlyHeaders.getOrDefault(key, key)
                        : key;
                PdfPCell headerCell = new PdfPCell(new Phrase(headerText, headerFont));
                headerCell.setBackgroundColor(Color.LIGHT_GRAY);
                headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(headerCell);
            }

            // Data Rows
            for (Map<String, Object> row : data) {
                for (String key : firstRow.keySet()) {
                    Object value = row.get(key);
                    String cellValue = value != null ? value.toString() : "";
                    PdfPCell cell = new PdfPCell(new Phrase(cellValue, cellFont));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
            }

            document.add(table);
            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to export PDF file", e);
        }
    }
}
