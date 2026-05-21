package querydsl.controller;

import querydsl.dto.PageResponse;
import querydsl.dto.PermissionDto;
import querydsl.dto.PermissionResponseDto;
import querydsl.dto.SavedQueryDto;
import querydsl.export.ExportService;
import querydsl.service.SavedQueryService;
import querydsl.export.ExportWithQueryRequest;
import querydsl.model.Permission;
import querydsl.query.AggregationRequest;
import querydsl.query.AggregationResult;
import querydsl.query.CursorPage;
import querydsl.query.CursorRequest;
import querydsl.query.EntityMetadata;
import querydsl.query.QueryRequest;
import querydsl.service.PermissionService;
import querydsl.service.QueryMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/permission")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permission CRUD and query endpoints")
public class PermissionController {

    private final PermissionService permissionService;
    private final ExportService exportService;
    private final QueryMetadataService metadataService;
    private final SavedQueryService savedQueryService;

    @PostMapping
    @Operation(summary = "Add a new permission")
    public ResponseEntity<PermissionResponseDto> addPermission(@Valid @RequestBody PermissionDto permissionDto) {
        PermissionResponseDto created = permissionService.addPermission(permissionDto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all permissions")
    public ResponseEntity<PageResponse<PermissionResponseDto>> getAllPermissions(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(permissionService.getAllPermissions(pageable)));
    }

    @GetMapping("/metadata")
    @Operation(summary = "Describe queryable fields, types and valid operations")
    public ResponseEntity<EntityMetadata> metadata() {
        return ResponseEntity.ok(metadataService.describe(Permission.class));
    }

    @GetMapping("/saved-queries")
    @Operation(summary = "List my saved permission queries")
    public ResponseEntity<java.util.List<SavedQueryDto.Response>> listSavedQueries() {
        return ResponseEntity.ok(savedQueryService.list("permission"));
    }

    @PostMapping("/saved-queries")
    @Operation(summary = "Save a named permission query")
    public ResponseEntity<SavedQueryDto.Response> createSavedQuery(@Valid @RequestBody SavedQueryDto.Request req) {
        return ResponseEntity.status(201).body(savedQueryService.create("permission", req));
    }

    @DeleteMapping("/saved-queries/{savedQueryId}")
    @Operation(summary = "Delete one of my saved permission queries")
    public ResponseEntity<Void> deleteSavedQuery(@PathVariable Long savedQueryId) {
        savedQueryService.delete("permission", savedQueryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/query")
    @Operation(summary = "Query permissions (recursive AND/OR groups; optional projection via select)")
    public ResponseEntity<PageResponse<?>> queryPermissions(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @Valid @RequestBody QueryRequest queryRequest) {
        if (queryRequest.getSelect() != null && !queryRequest.getSelect().isEmpty()) {
            return ResponseEntity.ok(PageResponse.from(permissionService.getAllPermissionsProjected(pageable, queryRequest)));
        }
        return ResponseEntity.ok(PageResponse.from(permissionService.getAllPermissionsByQueryRequest(pageable, queryRequest)));
    }

    @PostMapping("/query/cursor")
    @Operation(summary = "Keyset (cursor) pagination over permissions")
    public ResponseEntity<CursorPage<PermissionResponseDto>> queryPermissionsByCursor(
            @Valid @RequestBody CursorRequest request) {
        return ResponseEntity.ok(
                permissionService.queryPermissionsByCursor(request.query(), request.cursor(), request.size()));
    }

    @PostMapping("/aggregate")
    @Operation(summary = "Group-by + metric aggregation over permissions")
    public ResponseEntity<AggregationResult> aggregatePermissions(@Valid @RequestBody AggregationRequest request) {
        return ResponseEntity.ok(permissionService.aggregatePermissions(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get permission by ID")
    public ResponseEntity<PermissionResponseDto> getPermissionById(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update permission")
    public ResponseEntity<Void> updatePermission(@PathVariable Long id, @Valid @RequestBody PermissionDto permissionDto) {
        permissionService.updatePermission(id, permissionDto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete permission")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/count")
    @Operation(summary = "Count permissions matching query conditions")
    public ResponseEntity<Long> countPermissions(@Valid @RequestBody QueryRequest queryRequest) {
        return ResponseEntity.ok(permissionService.countPermissionsByQueryRequest(queryRequest));
    }

    @PostMapping("/exists")
    @Operation(summary = "Check if any permission matches query conditions")
    public ResponseEntity<Boolean> existsPermissions(@Valid @RequestBody QueryRequest queryRequest) {
        return ResponseEntity.ok(permissionService.existsPermissionByQueryRequest(queryRequest));
    }

    @PostMapping("/export/query")
    @Operation(summary = "Export permissions matching query conditions to Excel/PDF")
    public ResponseEntity<byte[]> exportByQuery(@Valid @RequestBody ExportWithQueryRequest request) {
        List<Permission> permissions = permissionService.getAllPermissionsByQueryRequestForExport(request.getQueryRequest());
        return exportService.buildExportResponse(permissions, request.toExportRequest(), "permissions");
    }
}
