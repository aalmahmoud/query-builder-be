package querydsl.service;

import querydsl.dto.PermissionDto;
import querydsl.dto.PermissionResponseDto;
import querydsl.exception.EntityNotFoundException;
import querydsl.mapper.PermissionMapper;
import querydsl.model.Permission;
import querydsl.query.AggregationRequest;
import querydsl.query.AggregationResult;
import querydsl.query.CursorPage;
import querydsl.query.QueryRequest;
import querydsl.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;
    private final GenericQueryService genericQueryService;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PermissionResponseDto addPermission(PermissionDto permissionDto) {
        Permission permission = permissionMapper.toEntity(permissionDto);
        // Review fix 5.13: return the created resource for a 201 Created response.
        // IDENTITY generation populates permission.id on save, so map the same instance.
        permissionRepository.save(permission);
        log.info("Permission created: {}", permission.getName());
        return permissionMapper.toPermissionResponseDto(permission);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updatePermission(Long id, PermissionDto permissionDto) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Permission", id));
        permissionMapper.updateEntityFromDto(permissionDto, permission);
        permissionRepository.save(permission);
        log.info("Permission updated: {}", permission.getName());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deletePermission(Long id) {
        if (!permissionRepository.existsById(id)) {
            throw new EntityNotFoundException("Permission", id);
        }
        permissionRepository.deleteById(id);
        log.info("Permission deleted with id: {}", id);
    }

    public PermissionResponseDto getPermissionById(Long id) {
        return permissionRepository.findById(id)
                .map(permissionMapper::toPermissionResponseDto)
                .orElseThrow(() -> new EntityNotFoundException("Permission", id));
    }

    public Page<PermissionResponseDto> getAllPermissions(Pageable pageable) {
        return permissionRepository.findAll(pageable)
                .map(permissionMapper::toPermissionResponseDto);
    }

    public Page<PermissionResponseDto> getAllPermissionsByQueryRequest(Pageable pageable, QueryRequest queryRequest) {
        return genericQueryService.findAllByQueryRequest(permissionRepository, queryRequest, pageable)
                .map(permissionMapper::toPermissionResponseDto);
    }

    public org.springframework.data.domain.Page<java.util.Map<String, Object>> getAllPermissionsProjected(
            Pageable pageable, QueryRequest queryRequest) {
        return genericQueryService.queryProjection(permissionRepository, queryRequest, pageable);
    }

    public AggregationResult aggregatePermissions(AggregationRequest request) {
        return genericQueryService.aggregate(permissionRepository, request);
    }

    public CursorPage<PermissionResponseDto> queryPermissionsByCursor(QueryRequest query, String cursor, Integer size) {
        CursorPage<Permission> page = genericQueryService.queryByCursor(permissionRepository, query, cursor, size);
        return new CursorPage<>(
                page.content().stream().map(permissionMapper::toPermissionResponseDto).toList(),
                page.nextCursor(), page.hasNext());
    }

    public List<Permission> getAllPermissionsByQueryRequestForExport(QueryRequest queryRequest) {
        return genericQueryService.findAllByQueryRequest(permissionRepository, queryRequest);
    }

    public long countPermissionsByQueryRequest(QueryRequest queryRequest) {
        return genericQueryService.countByQueryRequest(permissionRepository, queryRequest);
    }

    public boolean existsPermissionByQueryRequest(QueryRequest queryRequest) {
        return genericQueryService.existsByQueryRequest(permissionRepository, queryRequest);
    }
}
