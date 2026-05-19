package querydsl.export;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Phase 4 fix 4.9: validation rules so junk payloads are rejected at the controller
 * boundary. {@code format} accepts null (defaults to PDF in {@link ExportService} for
 * backward compatibility) but rejects anything other than {@code EXCEL} or {@code PDF}.
 * {@code selectedColumns} is capped so a 10k-column payload can't DoS the exporter.
 */
@Data
public class ExportRequest {

    @Size(max = 100, message = "selectedColumns may contain at most 100 entries")
    private List<String> selectedColumns;

    @Pattern(regexp = "(?i)EXCEL|PDF",
            message = "format must be EXCEL or PDF (case-insensitive)")
    private String format;

    private Map<String, String> friendlyHeaders;
}
