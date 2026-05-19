package querydsl.service;

import querydsl.dto.UserDto;
import querydsl.dto.UserResponseDto;
import querydsl.exception.EntityNotFoundException;
import querydsl.mapper.UserMapper;
import querydsl.model.Role;
import querydsl.model.User;
import querydsl.query.QueryCondition;
import querydsl.query.QueryOperation;
import querydsl.query.QueryRequest;
import querydsl.repository.RoleRepository;
import querydsl.repository.UserRepository;
import querydsl.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final GenericQueryService genericQueryService;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void addUser(UserDto userDto) {
        Role role = resolveRole(userDto.getRoleId());
        User user = userMapper.toEntity(userDto, role);

        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        // Phase 4 fix 4.15: keep the deterministic HMAC in lock-step with nationalId
        // for the unique index + later equality lookups.
        user.setNationalIdHash(encryptionService.hmac(user.getNationalId()));

        userRepository.save(user);
        log.info("User created: {}", user.getEmail());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));
        Role role = resolveRole(userDto.getRoleId());
        userMapper.updateEntityFromDto(userDto, user, role);

        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        user.setNationalIdHash(encryptionService.hmac(user.getNationalId()));

        userRepository.save(user);
        log.info("User updated: {}", user.getEmail());
    }

    /**
     * Resolves a role-by-id, returning {@code null} if the input is {@code null}
     * (caller wants no role assignment). Throws {@link EntityNotFoundException} for
     * a non-null but unknown id so that bad requests fail loud.
     */
    private Role resolveRole(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role", roleId));
    }

    /**
     * Toggles a user's active flag.
     *
     * <p><strong>Known issue (deferred to v2):</strong> the endpoint takes no body, so
     * concurrent callers can race — two simultaneous toggles return state to its
     * starting value. A future API version will accept {@code {"isActive": false}}
     * for idempotent semantics. Until then, treat this as fire-once.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void changeUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));
        user.setIsActive(!user.getIsActive());
        userRepository.save(user);
        log.info("User status changed: {} -> {}", user.getEmail(), user.getIsActive());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("User", id);
        }
        userRepository.deleteById(id);
        log.info("User deleted with id: {}", id);
    }

    public UserResponseDto getUserById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toUserResponseDto)
                .orElseThrow(() -> new EntityNotFoundException("User", id));
    }

    public Page<UserResponseDto> getAllUsers(Pageable pageable, Long roleId) {
        if (roleId == null) {
            return userRepository.findAll(pageable)
                    .map(userMapper::toUserResponseDto);
        }

        QueryCondition condition = new QueryCondition();
        condition.setField("role.id");
        condition.setOperation(QueryOperation.EQUALS);
        condition.setValue(roleId);

        return genericQueryService.findAllByQueryRequest(
                        userRepository, new QueryRequest(condition), pageable)
                .map(userMapper::toUserResponseDto);
    }

    public Page<UserResponseDto> getAllUsersByQueryRequest(Pageable pageable, QueryRequest queryRequest) {
        return genericQueryService.findAllByQueryRequest(userRepository, queryRequest, pageable)
                .map(userMapper::toUserResponseDto);
    }

    public List<User> getAllUsersByQueryRequestForExport(QueryRequest queryRequest) {
        return genericQueryService.findAllByQueryRequest(userRepository, queryRequest);
    }

    public long countUsersByQueryRequest(QueryRequest queryRequest) {
        return genericQueryService.countByQueryRequest(userRepository, queryRequest);
    }

    public boolean existsUserByQueryRequest(QueryRequest queryRequest) {
        return genericQueryService.existsByQueryRequest(userRepository, queryRequest);
    }
}
