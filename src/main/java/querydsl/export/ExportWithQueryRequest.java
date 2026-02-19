package querydsl.export;

import querydsl.query.QueryRequest;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ExportWithQueryRequest {
    private List<String> selectedColumns;
    private String format;
    private Map<String, String> friendlyHeaders;
    private QueryRequest queryRequest;

    public ExportRequest toExportRequest() {
        ExportRequest req = new ExportRequest();
        req.setSelectedColumns(selectedColumns);
        req.setFormat(format);
        req.setFriendlyHeaders(friendlyHeaders);
        return req;
    }
}
