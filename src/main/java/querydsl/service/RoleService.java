package querydsl.service;

import querydsl.dto.RoleDto;
import querydsl.dto.RoleResponseDto;
import querydsl.exception.EntityNotFoundException;
import querydsl.mapper.RoleMapper;
import querydsl.model.Role;
import querydsl.query.QueryRequest;
import querydsl.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;
    private final GenericQueryService genericQueryService;

    @Transactional
    public void addRole(RoleDto roleDto) {
        Role role = roleMapper.toEntity(roleDto);
        roleRepository.save(role);
        log.info("Role created: {}", role.getName());
    }

    @Transactional
    public void updateRole(Long id, RoleDto roleDto) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role", id));
        roleMapper.updateEntityFromDto(roleDto, role);
        roleRepository.save(role);
        log.info("Role updated: {}", role.getName());
    }

    @Transactional
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
