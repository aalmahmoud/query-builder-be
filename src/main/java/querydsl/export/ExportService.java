package querydsl.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import querydsl.exception.QueryException;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Generic export service.
 *
 * <p>Phase 1 fix 1.1: every entity that participates in exports MUST declare an
 * {@link Exportable} allow-list of dot-notation paths. Any column requested by a
 * client that is not on the allow-list is rejected. Navigation goes through public
 * getters only — the previous {@code setAccessible(true)} fallback that leaked
 * private fields (including {@code User.password}) has been removed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    /** Entity class → frozen set of exportable paths. Populated lazily, never invalidated. */
    private static final Map<Class<?>, Set<String>> ALLOWED_PATHS_CACHE = new ConcurrentHashMap<>();

    public <T> ResponseEntity<byte[]> buildExportResponse(List<T> entities, ExportRequest request, String filePrefix) {
        byte[] data = exportData(entities, request);

        boolean isExcel = "EXCEL".equalsIgnoreCase(request.getFormat());
        String extension = isExcel ? ".xlsx" : ".pdf";
        String contentType = isExcel ? EXCEL_CONTENT_TYPE : PDF_CONTENT_TYPE;
        String filename = filePrefix + "_" + LocalDate.now(ZoneOffset.UTC) + extension;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }

    public <T> byte[] exportData(List<T> entities, ExportRequest request) {
        if (entities == null || entities.isEmpty()) {
            log.warn("Empty entity list provided for export");
            return new byte[0];
        }

        List<String> requested = request.getSelectedColumns();
        if (requested == null || requested.isEmpty()) {
            log.warn("No columns selected for export");
            return new byte[0];
        }

        Class<?> entityClass = entities.get(0).getClass();
        Set<String> allowed = allowedPaths(entityClass);

        for (String col : requested) {
            if (!allowed.contains(col)) {
                throw new QueryException(
                        "Column '" + col + "' is not exportable on "
                                + simpleNameOf(entityClass) + ". Allowed: " + new TreeSet<>(allowed));
            }
        }

        List<Map<String, Object>> data = entities.stream()
                .map(entity -> extractRow(entity, requested))
                .collect(Collectors.toList());

        if ("EXCEL".equalsIgnoreCase(request.getFormat())) {
            return ExcelExporter.export(data, request.getFriendlyHeaders());
        }
        return PdfExporter.export(data, request.getFriendlyHeaders());
    }

    /**
     * Resolves the {@link Exportable} allow-list for an entity. Walks up the class
     * hierarchy so Hibernate proxies (which subclass the entity) also work.
     */
    private static Set<String> allowedPaths(Class<?> entityClass) {
        return ALLOWED_PATHS_CACHE.computeIfAbsent(entityClass, c -> {
            for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
                Exportable ann = cur.getAnnotation(Exportable.class);
                if (ann != null) {
                    return Set.of(ann.fields());
                }
            }
            throw new QueryException(simpleNameOf(c)
                    + " is not annotated with @Exportable; refusing to export.");
        });
    }

    private Map<String, Object> extractRow(Object entity, List<String> columns) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String col : columns) {
            try {
                row.put(col, walkGetters(entity, col));
            } catch (Exception ex) {
                log.warn("Failed to read '{}' from {}: {}", col,
                        simpleNameOf(entity.getClass()), ex.getMessage());
                row.put(col, "");
            }
        }
        return row;
    }

    /**
     * Walks a dotted path using public getters only. No private-field fallback,
     * no {@code setAccessible(true)}.
     */
    private Object walkGetters(Object entity, String path) throws Exception {
        Object current = entity;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = invokeGetter(current, segment);
        }
        return current;
    }

    private Object invokeGetter(Object target, String fieldName) throws Exception {
        String cap = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        Class<?> targetClass = target.getClass();
        try {
            Method getter = targetClass.getMethod("get" + cap);
            return getter.invoke(target);
        } catch (NoSuchMethodException ignored) {
            // try is*
        }
        try {
            Method getter = targetClass.getMethod("is" + cap);
            return getter.invoke(target);
        } catch (NoSuchMethodException ignored) {
            // fall through
        }
        throw new NoSuchMethodException(
                "No public getter for '" + fieldName + "' on " + simpleNameOf(targetClass));
    }

    private static String simpleNameOf(Class<?> c) {
        // Hibernate proxy classes have ugly synthetic names; report the entity name instead.
        Class<?> cur = c;
        while (cur != null && cur.getSimpleName().contains("$HibernateProxy")) {
            cur = cur.getSuperclass();
        }
        return cur != null ? cur.getSimpleName() : c.getSimpleName();
    }
}
