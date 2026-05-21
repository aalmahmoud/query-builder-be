package querydsl.service;

import querydsl.dto.RoleDto;
import querydsl.dto.RoleResponseDto;
import querydsl.exception.EntityNotFoundException;
import querydsl.mapper.RoleMapper;
import querydsl.model.Permission;
import querydsl.model.Role;
import querydsl.query.AggregationRequest;
import querydsl.query.AggregationResult;
import querydsl.query.CursorPage;
import querydsl.query.QueryRequest;
import querydsl.repository.PermissionRepository;
import querydsl.repository.RoleRepository;

import java.util.HashSet;
import java.util.Set;
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
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;
    private final GenericQueryService genericQueryService;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public RoleResponseDto addRole(RoleDto roleDto) {
        Set<Permission> permissions = resolvePermissions(roleDto.getPermissionIds());
        Role role = roleMapper.toEntity(roleDto, permissions);
        // Review fix 5.13: return the created resource for a 201 Created response.
        // IDENTITY generation populates role.id on save, so map the same instance.
        roleRepository.save(role);
        log.info("Role created: {}", role.getName());
        return roleMapper.toRoleResponseDto(role);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateRole(Long id, RoleDto roleDto) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role", id));
        Set<Permission> permissions = roleDto.getPermissionIds() != null
                ? resolvePermissions(roleDto.getPermissionIds())
                : null;
        roleMapper.updateEntityFromDto(roleDto, role, permissions);
        roleRepository.save(role);
        log.info("Role updated: {}", role.getName());
    }

    private Set<Permission> resolvePermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Permission> resolved = new HashSet<>(permissionIds.size());
        for (Long id : permissionIds) {
            resolved.add(permissionRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Permission", id)));
        }
        return resolved;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new EntityNotFoundException("Role", id);
        }
        roleRepository.deleteById(id);
        log.info("Role deleted with id: {}", id);
    }

    public RoleResponseDto getRoleById(Long id) {
        return roleRepository.findById(id)
                .map(roleMapper::toRoleResponseDto)
                .orElseThrow(() -> new EntityNotFoundException("Role", id));
    }

    public Page<RoleResponseDto> getAllRoles(Pageable pageable) {
        return roleRepository.findAll(pageable)
                .map(roleMapper::toRoleResponseDto);
    }

    public Page<RoleResponseDto> getAllRolesByQueryRequest(Pageable pageable, QueryRequest queryRequest) {
        return genericQueryService.findAllByQueryRequest(roleRepository, queryRequest, pageable)
                .map(roleMapper::toRoleResponseDto);
    }

    public org.springframework.data.domain.Page<java.util.Map<String, Object>> getAllRolesProjected(
            Pageable pageable, QueryRequest queryRequest) {
        return genericQueryService.queryProjection(roleRepository, queryRequest, pageable);
    }

    public AggregationResult aggregateRoles(AggregationRequest request) {
        return genericQueryService.aggregate(roleRepository, request);
    }

    public CursorPage<RoleResponseDto> queryRolesByCursor(QueryRequest query, String cursor, Integer size) {
        CursorPage<Role> page = genericQueryService.queryByCursor(roleRepository, query, cursor, size);
        return new CursorPage<>(
                page.content().stream().map(roleMapper::toRoleResponseDto).toList(),
                page.nextCursor(), page.hasNext());
    }

    public List<Role> getAllRolesByQueryRequestForExport(QueryRequest queryRequest) {
        return genericQueryService.findAllByQueryRequest(roleRepository, queryRequest);
    }

    public long countRolesByQueryRequest(QueryRequest queryRequest) {
        return genericQueryService.countByQueryRequest(roleRepository, queryRequest);
    }

    public boolean existsRoleByQueryRequest(QueryRequest queryRequest) {
        return genericQueryService.existsByQueryRequest(roleRepository, queryRequest);
    }
}
