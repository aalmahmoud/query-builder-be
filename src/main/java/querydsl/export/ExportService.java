package querydsl.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generic export service that works with any entity type.
 * Uses reflection to extract field values dynamically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    /**
     * Exports entities and builds a downloadable HTTP response.
     *
     * @param entities   list of entities to export
     * @param request    export parameters (columns, format, headers)
     * @param filePrefix prefix for the generated filename (e.g. "users", "roles")
     * @return ResponseEntity with the file bytes and appropriate headers
     */
    public <T> ResponseEntity<byte[]> buildExportResponse(List<T> entities, ExportRequest request, String filePrefix) {
        byte[] data = exportData(entities, request);

        boolean isExcel = "EXCEL".equalsIgnoreCase(request.getFormat());
        String extension = isExcel ? ".xlsx" : ".pdf";
        String contentType = isExcel ? EXCEL_CONTENT_TYPE : PDF_CONTENT_TYPE;
        String filename = filePrefix + "_" + LocalDate.now() + extension;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }

    /**
     * Export data from any entity list to Excel or PDF format.
     *
     * @param entities list of entities to export
     * @param request  export request with column selection and format
     * @return byte array containing the exported file
     */
    public <T> byte[] exportData(List<T> entities, ExportRequest request) {
        if (entities == null || entities.isEmpty()) {
            log.warn("Empty entity list provided for export");
            return new byte[0];
        }

        List<Map<String, Object>> data = entities.stream()
                .map(entity -> filterColumns(entity, request.getSelectedColumns()))
                .collect(Collectors.toList());

        if ("EXCEL".equalsIgnoreCase(request.getFormat())) {
            return ExcelExporter.export(data, request.getFriendlyHeaders());
        } else {
            return PdfExporter.export(data, request.getFriendlyHeaders());
        }
    }

    /**
     * Extract selected columns from an entity using reflection
     * 
     * @param entity The entity to extract data from
     * @param selectedColumns List of column names to extract
     * @return Map of column name to value
     */
    private <T> Map<String, Object> filterColumns(T entity, List<String> selectedColumns) {
        Map<String, Object> map = new LinkedHashMap<>();

        if (selectedColumns == null || selectedColumns.isEmpty()) {
            log.warn("No columns selected for export, returning empty map");
            return map;
        }

        Class<?> entityClass = entity.getClass();

        for (String col : selectedColumns) {
            try {
                Object value = getFieldValue(entity, entityClass, col);
                // Use friendly header if available, otherwise use column name as-is
                map.put(col, value);
            } catch (Exception e) {
                log.warn("Failed to extract field '{}' from entity {}: {}", col, entityClass.getSimpleName(), e.getMessage());
                map.put(col, "");
            }
        }

        return map;
    }

    /**
     * Get field value from entity using reflection
     * Supports both direct field access and getter methods
     * 
     * @param entity The entity instance
     * @param entityClass The entity class
     * @param fieldName The field name (supports nested fields with dot notation)
     * @return The field value
     */
    private Object getFieldValue(Object entity, Class<?> entityClass, String fieldName) {
        if (entity == null) {
            return null;
        }

        // Handle nested fields (e.g., "role.name")
        if (fieldName.contains(".")) {
            String[] parts = fieldName.split("\\.", 2);
            Object nestedEntity = getFieldValue(entity, entityClass, parts[0]);
            if (nestedEntity == null) {
                return null;
            }
            return getFieldValue(nestedEntity, nestedEntity.getClass(), parts[1]);
        }

        // Try getter method first (e.g., getFirstName, isActive)
        String getterName = "get" + capitalize(fieldName);
        try {
            Method getter = entityClass.getMethod(getterName);
            return getter.invoke(entity);
        } catch (NoSuchMethodException e) {
            // Try boolean getter (e.g., isActive)
            if (fieldName.startsWith("is") || fieldName.startsWith("has")) {
                try {
                    Method booleanGetter = entityClass.getMethod(fieldName);
                    return booleanGetter.invoke(entity);
                } catch (Exception ex) {
                    // Fall through to field access
                }
            }
        } catch (Exception e) {
            log.debug("Getter method failed for field '{}', trying direct field access: {}", fieldName, e.getMessage());
        }

        // Try direct field access
        try {
            java.lang.reflect.Field field = entityClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(entity);
        } catch (NoSuchFieldException e) {
            log.warn("Field '{}' not found in entity {}", fieldName, entityClass.getSimpleName());
            return null;
        } catch (Exception e) {
            log.warn("Failed to access field '{}' in entity {}: {}", fieldName, entityClass.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Capitalize first letter of a string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
