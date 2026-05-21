package querydsl.service;

import querydsl.dto.UserDto;
import querydsl.dto.UserResponseDto;
import querydsl.exception.EntityNotFoundException;
import querydsl.mapper.UserMapper;
import querydsl.model.Role;
import querydsl.model.User;
import querydsl.query.AggregationRequest;
import querydsl.query.AggregationResult;
import querydsl.query.CursorPage;
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
import java.util.Locale;
import java.util.Map;

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
    public UserResponseDto addUser(UserDto userDto) {
        Role role = resolveRole(userDto.getRoleId());
        User user = userMapper.toEntity(userDto, role);
        // Phase 5 fix 5.16: normalise email so "Foo@Bar.com" and "foo@bar.com" don't
        // both register as distinct users.
        user.setEmail(normaliseEmail(user.getEmail()));

        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        // Phase 4 fix 4.15: keep the deterministic HMAC in lock-step with nationalId
        // for the unique index + later equality lookups.
        user.setNationalIdHash(encryptionService.hmac(user.getNationalId()));

        // Review fix 5.13: return the created resource so the controller can answer
        // 201 Created with a Location header and body instead of an empty 200.
        // IDENTITY generation populates user.id on save, so map the same instance.
        userRepository.save(user);
        log.info("User created: {}", user.getEmail());
        return userMapper.toUserResponseDto(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));
        Role role = resolveRole(userDto.getRoleId());
        userMapper.updateEntityFromDto(userDto, user, role);
        user.setEmail(normaliseEmail(user.getEmail()));

        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        user.setNationalIdHash(encryptionService.hmac(user.getNationalId()));

        userRepository.save(user);
        log.info("User updated: {}", user.getEmail());
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
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
     * Sets a user's active flag to an absolute target state (review fix 4.9).
     *
     * <p>Taking the desired {@code isActive} value rather than toggling makes the
     * operation idempotent: repeated or concurrent calls converge on the same state
     * instead of racing back to where they started.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void changeUserStatus(Long id, boolean isActive) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));
        user.setIsActive(isActive);
        userRepository.save(user);
        log.info("User status set: {} -> {}", user.getEmail(), isActive);
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

    /** Projected query (sparse fieldset) — returns flat maps keyed by the requested paths. */
    public Page<Map<String, Object>> getAllUsersProjected(Pageable pageable, QueryRequest queryRequest) {
        return genericQueryService.queryProjection(userRepository, queryRequest, pageable);
    }

    /** Group-by + metric aggregation over users. */
    public AggregationResult aggregateUsers(AggregationRequest request) {
        return genericQueryService.aggregate(userRepository, request);
    }

    /** Keyset (cursor) pagination over users (stable createdDate/id ordering). */
    public CursorPage<UserResponseDto> queryUsersByCursor(QueryRequest query, String cursor, Integer size) {
        CursorPage<User> page = genericQueryService.queryByCursor(userRepository, query, cursor, size);
        return new CursorPage<>(
                page.content().stream().map(userMapper::toUserResponseDto).toList(),
                page.nextCursor(), page.hasNext());
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
