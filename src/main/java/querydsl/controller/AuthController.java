package querydsl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import querydsl.dto.LoginRequest;
import querydsl.dto.RefreshRequest;
import querydsl.model.User;
import querydsl.repository.UserRepository;
import querydsl.security.JwtTokenProvider;
import querydsl.security.RefreshTokenService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    @Operation(summary = "Login and obtain access + refresh tokens")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String accessToken = tokenProvider.generateToken(authentication);
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth != null && !auth.isEmpty())
                .collect(Collectors.joining(","));

        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        String refreshToken = refreshTokenService.issueFor(user);

        // LinkedHashMap to keep the original four keys before the additive refreshToken.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", accessToken);
        body.put("type", "Bearer");
        body.put("username", authentication.getName());
        body.put("authorities", authorities);
        body.put("refreshToken", refreshToken);
        return ResponseEntity.ok(body);
    }

    /**
     * Phase 3 fix 3.3: trade a valid refresh token for a fresh access token (and a rotated
     * refresh token, so a stolen token has a single-use horizon).
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest request) {
        User user = refreshTokenService.validateAndGetUser(request.getRefreshToken());

        // Rotate: revoke the presented token and issue a new one.
        refreshTokenService.revoke(request.getRefreshToken());
        String newRefresh = refreshTokenService.issueFor(user);

        String authorities = user.getRole() != null
                ? "ROLE_" + user.getRole().getName().toUpperCase()
                : "";
        String accessToken = tokenProvider.generateToken(user.getEmail(), authorities);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", accessToken);
        body.put("type", "Bearer");
        body.put("username", user.getEmail());
        body.put("refreshToken", newRefresh);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token (logout on this device)")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
