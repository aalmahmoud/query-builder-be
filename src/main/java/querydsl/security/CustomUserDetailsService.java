package querydsl.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import querydsl.model.User;
import querydsl.repository.UserRepository;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Custom UserDetailsService implementation.
 * 
 * <p>Loads user from database and converts to Spring Security UserDetails.
 * Includes roles and permissions in authorities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User appUser = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
        
        if (!appUser.getIsActive()) {
            throw new UsernameNotFoundException("User is not active: " + username);
        }
        
        Collection<GrantedAuthority> authorities = getAuthorities(appUser);
        
        return org.springframework.security.core.userdetails.User.builder()
                .username(appUser.getEmail())
                .password(appUser.getPassword() != null ? appUser.getPassword() : "")
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!appUser.getIsActive())
                .build();
    }
    
    /**
     * Get authorities from user's role and permissions
     */
    private Collection<GrantedAuthority> getAuthorities(User user) {
        Stream<String> roleAuthorities = Stream.empty();
        Stream<String> permissionAuthorities = Stream.empty();
        
        // Add role authority if user has a role
        if (user.getRole() != null) {
            roleAuthorities = Stream.of("ROLE_" + user.getRole().getName().toUpperCase());
            
            // Add permission authorities if role has permissions
            if (user.getRole().getPermissions() != null && !user.getRole().getPermissions().isEmpty()) {
                permissionAuthorities = user.getRole().getPermissions().stream()
                        .map(permission -> permission.getResource() + ":" + permission.getAction());
            }
        }
        
        return Stream.concat(roleAuthorities, permissionAuthorities)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
