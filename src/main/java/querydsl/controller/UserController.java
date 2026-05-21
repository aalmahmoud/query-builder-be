package querydsl.controller;

import querydsl.dto.ChangeUserStatusRequest;
import querydsl.dto.PageResponse;
import querydsl.dto.SavedQueryDto;
import querydsl.dto.UserDto;
import querydsl.dto.UserResponseDto;
import querydsl.export.ExportService;
import querydsl.service.SavedQueryService;
import querydsl.export.ExportWithQueryRequest;
import querydsl.model.User;
import querydsl.query.AggregationRequest;
import querydsl.query.AggregationResult;
import querydsl.query.CursorPage;
import querydsl.query.CursorRequest;
import querydsl.query.EntityMetadata;
import querydsl.query.QueryRequest;
import querydsl.service.QueryMetadataService;
import querydsl.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import querydsl.dto.validation.OnCreate;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User CRUD and query endpoints")
public class UserController {

    private final UserService userService;
    private final ExportService exportService;
    private final QueryMetadataService metadataService;
    private final SavedQueryService savedQueryService;

    @PostMapping
    @Operation(summary = "Add a new user")
    public ResponseEntity<UserResponseDto> addUser(
            @Validated({OnCreate.class, Default.class}) @RequestBody UserDto userDto) {
        UserResponseDto created = userService.addUser(userDto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Get all users")
    public ResponseEntity<PageResponse<UserResponseDto>> getAllUsers(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) Long roleId) {
        return ResponseEntity.ok(PageResponse.from(userService.getAllUsers(pageable, roleId)));
    }

    @GetMapping("/metadata")
    @Operation(summary = "Describe queryable fields, types and valid operations")
    public ResponseEntity<EntityMetadata> metadata() {
        return ResponseEntity.ok(metadataService.describe(User.class));
    }

    @GetMapping("/saved-queries")
    @Operation(summary = "List my saved user queries")
    public ResponseEntity<java.util.List<SavedQueryDto.Response>> listSavedQueries() {
        return ResponseEntity.ok(savedQueryService.list("user"));
    }

    @PostMapping("/saved-queries")
    @Operation(summary = "Save a named user query")
    public ResponseEntity<SavedQueryDto.Response> createSavedQuery(@Valid @RequestBody SavedQueryDto.Request req) {
        return ResponseEntity.status(201).body(savedQueryService.create("user", req));
    }

    @DeleteMapping("/saved-queries/{savedQueryId}")
    @Operation(summary = "Delete one of my saved user queries")
    public ResponseEntity<Void> deleteSavedQuery(@PathVariable Long savedQueryId) {
        savedQueryService.delete("user", savedQueryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/query")
    @Operation(summary = "Query users (recursive AND/OR groups; optional projection via select)")
    public ResponseEntity<PageResponse<?>> queryUsers(
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            @Valid @RequestBody QueryRequest queryRequest) {
        if (queryRequest.getSelect() != null && !queryRequest.getSelect().isEmpty()) {
            return ResponseEntity.ok(PageResponse.from(userService.getAllUsersProjected(pageable, queryRequest)));
        }
        return ResponseEntity.ok(PageResponse.from(userService.getAllUsersByQueryRequest(pageable, queryRequest)));
    }

    @PostMapping("/query/cursor")
    @Operation(summary = "Keyset (cursor) pagination over users")
    public ResponseEntity<CursorPage<UserResponseDto>> queryUsersByCursor(
            @Valid @RequestBody CursorRequest request) {
        return ResponseEntity.ok(
                userService.queryUsersByCursor(request.query(), request.cursor(), request.size()));
    }

    @PostMapping("/aggregate")
    @Operation(summary = "Group-by + metric aggregation over users")
    public ResponseEntity<AggregationResult> aggregateUsers(@Valid @RequestBody AggregationRequest request) {
        return ResponseEntity.ok(userService.aggregateUsers(request));
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
