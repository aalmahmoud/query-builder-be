package querydsl.service;

import querydsl.dto.UserDto;
import querydsl.dto.UserResponseDto;
import querydsl.exception.EntityNotFoundException;
import querydsl.mapper.UserMapper;
import querydsl.model.User;
import querydsl.query.QueryCondition;
import querydsl.query.QueryOperation;
import querydsl.query.QueryRequest;
import querydsl.repository.UserRepository;
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
    private final UserMapper userMapper;
    private final GenericQueryService genericQueryService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void addUser(UserDto userDto) {
        User user = userMapper.toEntity(userDto);

        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }

        userRepository.save(user);
        log.info("User created: {}", user.getEmail());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateUser(Long id, UserDto userDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));
        userMapper.updateEntityFromDto(userDto, user);

        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }

        userRepository.save(user);
        log.info("User updated: {}", user.getEmail());
    }

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
