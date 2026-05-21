package querydsl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import querydsl.query.EntityMetadata;
import querydsl.query.FieldMeta;
import querydsl.query.FilterableFields;
import querydsl.query.QueryOperation;
import querydsl.query.SortableFields;
import querydsl.query.computed.ComputedFieldHandlerRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds {@link EntityMetadata} for the self-describing {@code GET /{entity}/metadata}
 * endpoint. The queryable field set is the union of the entity's {@code @FilterableFields},
 * {@code @SortableFields}, and registered computed fields — so the metadata never advertises
 * a field the engine would reject. Field types (and therefore valid operations) are resolved
 * by reflecting the dotted path against the entity, walking the class hierarchy and through
 * {@code @ManyToOne}/{@code @OneToMany}-style relations.
 */
@Service
@Slf4j
public class QueryMetadataService {

    private final ComputedFieldHandlerRegistry computedRegistry;

    public QueryMetadataService(ComputedFieldHandlerRegistry computedRegistry) {
        this.computedRegistry = computedRegistry;
    }

    public EntityMetadata describe(Class<?> entityClass) {
        Set<String> sortable = annotationValues(entityClass.getAnnotation(SortableFields.class) == null
                ? null : entityClass.getAnnotation(SortableFields.class).value());
        FilterableFields ff = entityClass.getAnnotation(FilterableFields.class);
        Set<String> filterable = annotationValues(ff == null ? null : ff.value());
        Set<String> computed = computedRegistry.computedFieldNames(entityClass);

        // Ordered union: filterable first (the common case), then sortable-only, then computed.
        Set<String> names = new LinkedHashSet<>();
        names.addAll(filterable);
        names.addAll(sortable);
        names.addAll(computed);

        List<FieldMeta> fields = new ArrayList<>(names.size());
        for (String name : names) {
            boolean isComputed = computed.contains(name);
            String type = isComputed ? "string" : resolveType(entityClass, name);
            List<String> enumValues = "enum".equals(type) ? enumConstants(entityClass, name) : null;
            fields.add(new FieldMeta(
                    name,
                    humanize(name),
                    type,
                    operationsFor(type),
                    sortable.contains(name),
                    // computed fields are always filterable; otherwise honour the allow-list
                    // (when no @FilterableFields annotation exists, treat all listed fields as filterable)
                    isComputed || ff == null || filterable.contains(name),
                    isComputed,
                    enumValues));
        }
        return new EntityMetadata(simpleEntityName(entityClass), fields);
    }

    // ---- type resolution ----

    private String resolveType(Class<?> root, String path) {
        try {
            Class<?> type = resolveJavaType(root, path);
            return logicalType(type);
        } catch (Exception e) {
            log.debug("Could not resolve type for {}.{}: {}", root.getSimpleName(), path, e.getMessage());
            return "string";
        }
    }

    private Class<?> resolveJavaType(Class<?> root, String path) throws NoSuchFieldException {
        String[] segments = path.split("\\.");
        Class<?> current = root;
        Class<?> resolved = null;
        for (String segment : segments) {
            Field field = findField(current, segment);
            if (field == null) {
                throw new NoSuchFieldException(segment);
            }
            Class<?> fieldType = field.getType();
            if (Collection.class.isAssignableFrom(fieldType)) {
                // e.g. Set<Permission> permissions → element type for the next segment
                current = genericElementType(field);
                resolved = current;
            } else {
                current = fieldType;
                resolved = fieldType;
            }
        }
        return resolved;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Class<?> genericElementType(Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0
                && pt.getActualTypeArguments()[0] instanceof Class<?> elem) {
            return elem;
        }
        return Object.class;
    }

    private static String logicalType(Class<?> t) {
        if (t == null) return "string";
        if (t == String.class) return "string";
        if (t == Boolean.class || t == boolean.class) return "boolean";
        if (Number.class.isAssignableFrom(t) || t == int.class || t == long.class || t == double.class
                || t == float.class || t == short.class || t == byte.class
                || t == BigDecimal.class || t == BigInteger.class) return "number";
        if (t == LocalDate.class) return "date";
        if (t == LocalDateTime.class || t == Instant.class || t == OffsetDateTime.class
                || t == ZonedDateTime.class || t == LocalTime.class) return "datetime";
        if (t.isEnum()) return "enum";
        if (t == UUID.class) return "uuid";
        return "string";
    }

    private List<String> enumConstants(Class<?> root, String path) {
        try {
            Class<?> t = resolveJavaType(root, path);
            if (t != null && t.isEnum()) {
                List<String> out = new ArrayList<>();
                for (Object c : t.getEnumConstants()) {
                    out.add(((Enum<?>) c).name());
                }
                return out;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    // ---- operations per logical type (mirrors docs/CONTRACT.md §9) ----

    private static final List<String> NULL_OPS = List.of(
            QueryOperation.IS_NULL.name(), QueryOperation.IS_NOT_NULL.name());

    private static List<String> operationsFor(String type) {
        List<String> ops = new ArrayList<>();
        switch (type) {
            case "string" -> {
                ops.add(QueryOperation.EQUALS.name());
                ops.add(QueryOperation.NOT_EQUALS.name());
                ops.add(QueryOperation.CONTAINS.name());
                ops.add(QueryOperation.CONTAINS_IGNORE_CASE.name());
                ops.add(QueryOperation.NOT_CONTAINS.name());
                ops.add(QueryOperation.STARTS_WITH.name());
                ops.add(QueryOperation.STARTS_WITH_IGNORE_CASE.name());
                ops.add(QueryOperation.ENDS_WITH.name());
                ops.add(QueryOperation.ENDS_WITH_IGNORE_CASE.name());
                ops.add(QueryOperation.IN.name());
                ops.add(QueryOperation.NOT_IN.name());
            }
            case "number", "date", "datetime" -> {
                ops.add(QueryOperation.EQUALS.name());
                ops.add(QueryOperation.NOT_EQUALS.name());
                ops.add(QueryOperation.GREATER_THAN.name());
                ops.add(QueryOperation.GREATER_THAN_OR_EQUAL.name());
                ops.add(QueryOperation.LESS_THAN.name());
                ops.add(QueryOperation.LESS_THAN_OR_EQUAL.name());
                ops.add(QueryOperation.BETWEEN.name());
                ops.add(QueryOperation.NOT_BETWEEN.name());
                if ("number".equals(type)) {
                    ops.add(QueryOperation.IN.name());
                    ops.add(QueryOperation.NOT_IN.name());
                }
            }
            case "boolean" -> {
                ops.add(QueryOperation.EQUALS.name());
                ops.add(QueryOperation.IS_TRUE.name());
                ops.add(QueryOperation.IS_FALSE.name());
            }
            case "enum", "uuid" -> {
                ops.add(QueryOperation.EQUALS.name());
                ops.add(QueryOperation.NOT_EQUALS.name());
                ops.add(QueryOperation.IN.name());
                ops.add(QueryOperation.NOT_IN.name());
            }
            default -> ops.add(QueryOperation.EQUALS.name());
        }
        ops.addAll(NULL_OPS);
        return ops;
    }

    // ---- helpers ----

    private static Set<String> annotationValues(String[] values) {
        return values == null ? Set.of() : new LinkedHashSet<>(List.of(values));
    }

    private static String simpleEntityName(Class<?> entityClass) {
        String n = entityClass.getSimpleName();
        return n.isEmpty() ? n : Character.toLowerCase(n.charAt(0)) + n.substring(1);
    }

    /** "role.name" → "Role Name"; "createdDate" → "Created Date". */
    private static String humanize(String path) {
        String last = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < last.length(); i++) {
            char c = last.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                sb.append(' ').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
