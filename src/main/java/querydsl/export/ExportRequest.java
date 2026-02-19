package querydsl.export;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ExportRequest {
    private List<String> selectedColumns;
    private String format; // "EXCEL" or "PDF"
    private Map<String, String> friendlyHeaders;

}
