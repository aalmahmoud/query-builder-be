package querydsl.export;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import querydsl.query.QueryRequest;

import java.util.List;
import java.util.Map;

@Data
public class ExportWithQueryRequest {

    @Size(max = 100, message = "selectedColumns may contain at most 100 entries")
    private List<String> selectedColumns;

    @Pattern(regexp = "(?i)EXCEL|PDF",
            message = "format must be EXCEL or PDF (case-insensitive)")
    private String format;

    private Map<String, String> friendlyHeaders;

    @Valid
    private QueryRequest queryRequest;

    public ExportRequest toExportRequest() {
        ExportRequest req = new ExportRequest();
        req.setSelectedColumns(selectedColumns);
        req.setFormat(format);
        req.setFriendlyHeaders(friendlyHeaders);
        return req;
    }
}
