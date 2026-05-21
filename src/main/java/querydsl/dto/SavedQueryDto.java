package querydsl.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import querydsl.query.QueryRequest;

import java.time.LocalDateTime;

/**
 * Request + response DTOs for saved queries. The request carries a name and a
 * {@link QueryRequest}; the response echoes the persisted record (with owner/date).
 */
public class SavedQueryDto {

    @Data
    public static class Request {
        @NotBlank(message = "name is required")
        @Size(max = 150)
        private String name;

        @NotNull(message = "queryRequest is required")
        @Valid
        private QueryRequest queryRequest;
    }

    @Data
    public static class Response {
        private Long id;
        private String entity;
        private String name;
        private QueryRequest queryRequest;
        private String createdBy;
        private LocalDateTime createdDate;
    }
}
