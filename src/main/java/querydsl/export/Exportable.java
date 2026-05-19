package querydsl.export;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the dotted-path columns that may be requested for an entity via {@link ExportService}.
 *
 * <p>This is the security boundary that prevents arbitrary private-field disclosure
 * (e.g. {@code User.password}) through the export endpoint. {@link ExportService} reads
 * the {@code fields} list and rejects any requested column that is not present.
 *
 * <p>Paths may include nested navigation (e.g. {@code "role.name"}). Each segment is
 * resolved via the public getter only — there is no private-field fallback.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Exportable {
    String[] fields();
}
