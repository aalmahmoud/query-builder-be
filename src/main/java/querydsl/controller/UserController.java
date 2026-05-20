package querydsl.controller;

import querydsl.dto.ChangeUserStatusRequest;
import querydsl.dto.UserDto;
import querydsl.dto.UserResponseDto;
import querydsl.export.ExportService;
import querydsl.export.ExportWithQueryRequest;
import querydsl.model.User;
import querydsl.query.QueryRequest;
import querydsl.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import querydsl.dto.validation.OnCreate;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User CRUD and query endpoints")
public class UserController {

    private final UserService userService;
    private final ExportService exportService;

    @PostMapping
    @Operation(summary = "Add a new user")
    public ResponseEntity<Void> addUser(
            @Validated({OnCreate.class, Default.class}) @RequestBody UserDto userDto) {
        userService.addUser(userDto);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @Operation(summary = "Get all users")
    public ResponseEntity<Page<UserResponseDto>> getAllUsers(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) Long roleId) {
        return ResponseEntity.ok(userService.getAllUsers(pageable, roleId));
    }

    @PostMapping("/query")
    @Operation(summary = "Query users with dynamic conditions")
    public ResponseEntity<Page<UserResponseDto>> queryUsers(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @Valid @RequestBody QueryRequest queryRequest) {
        return ResponseEntity.ok(userService.getAllUsersByQueryRequest(pageable, queryRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    public ResponseEntity<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserDto userDto) {
        userService.updateUser(id, userDto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/change-status")
    @Operation(summary = "Set user active/inactive status")
    public ResponseEntity<Void> changeUserStatus(
            @PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest request) {
        userService.changeUserStatus(id, request.getIsActive());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/count")
    @Operation(summary = "Count users matching query conditions")
    public ResponseEntity<Long> countUsers(@Valid @RequestBody QueryRequest queryRequest) {
        return ResponseEntity.ok(userService.countUsersByQueryRequest(queryRequest));
    }

    @PostMapping("/exists")
    @Operation(summary = "Check if any user matches query conditions")
    public ResponseEntity<Boolean> existsUsers(@Valid @RequestBody QueryRequest queryRequest) {
        return ResponseEntity.ok(userService.existsUserByQueryRequest(queryRequest));
    }

    @PostMapping("/export/query")
    @Operation(summary = "Export users matching query conditions to Excel/PDF")
    public ResponseEntity<byte[]> exportByQuery(@Valid @RequestBody ExportWithQueryRequest request) {
        List<User> users = userService.getAllUsersByQueryRequestForExport(request.getQueryRequest());
        return exportService.buildExportResponse(users, request.toExportRequest(), "users");
    }
}
