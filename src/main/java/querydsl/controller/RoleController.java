package querydsl.controller;

import querydsl.dto.RoleDto;
import querydsl.dto.RoleResponseDto;
import querydsl.export.ExportService;
import querydsl.export.ExportWithQueryRequest;
import querydsl.model.Role;
import querydsl.query.QueryRequest;
import querydsl.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final ExportService exportService;

    @PostMapping
    @Operation(summary = "Add a new role")
    public ResponseEntity<Void> addRole(@Valid @RequestBody RoleDto roleDto) {
        roleService.addRole(roleDto);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @Operation(summary = "Get all roles")
    public ResponseEntity<Page<RoleResponseDto>> getAllRoles(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(roleService.getAllRoles(pageable));
    }

    @PostMapping("/query")
    @Operation(summary = "Query roles with dynamic conditions")
    public ResponseEntity<Page<RoleResponseDto>> queryRoles(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @Valid @RequestBody QueryRequest queryRequest) {
        return ResponseEntity.ok(roleService.getAllRolesByQueryRequest(pageable, queryRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get role by ID")
    public ResponseEntity<RoleResponseDto> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update role")
    public ResponseEntity<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleDto roleDto) {
        roleService.updateRole(id, roleDto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete role")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/count")
    @Operation(summary = "Count roles matching query conditions")
    public ResponseEntity<Long> countRoles(@Valid @RequestBody QueryRequest queryRequest) {
        return ResponseEntity.ok(roleService.countRolesByQueryRequest(queryRequest));
    }

    @PostMapping("/exists")
    @Operation(summary = "Check if any role matches query conditions")
    public ResponseEntity<Boolean> existsRoles(@Valid @RequestBody QueryRequest queryRequest) {
        return ResponseEntity.ok(roleService.existsRoleByQueryRequest(queryRequest));
    }

    @PostMapping("/export/query")
    @Operation(summary = "Export roles matching query conditions to Excel/PDF")
    public ResponseEntity<byte[]> exportByQuery(@Valid @RequestBody ExportWithQueryRequest request) {
        List<Role> roles = roleService.getAllRolesByQueryRequestForExport(request.getQueryRequest());
        return exportService.buildExportResponse(roles, request.toExportRequest(), "roles");
    }
}
