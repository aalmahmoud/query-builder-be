package querydsl.controller;

import querydsl.dto.PageResponse;
import querydsl.dto.PermissionDto;
import querydsl.dto.PermissionResponseDto;
import querydsl.export.ExportService;
import querydsl.export.ExportWithQueryRequest;
import querydsl.model.Permission;
import querydsl.query.QueryRequest;
import querydsl.service.PermissionService;
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

    @PostMapping("/query")
    @Operation(summary = "Query permissions with dynamic conditions")
    public ResponseEntity<PageResponse<PermissionResponseDto>> queryPermissions(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @Valid @RequestBody QueryRequest queryRequest) {
        return ResponseEntity.ok(PageResponse.from(permissionService.getAllPermissionsByQueryRequest(pageable, queryRequest)));
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
