package com.curamatrix.hsm.service;

import com.curamatrix.hsm.config.JwtUtil;
import com.curamatrix.hsm.dto.request.LoginRequest;
import com.curamatrix.hsm.dto.response.LoginResponse;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.entity.Role;
import com.curamatrix.hsm.exception.BruteForceProtectionException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.exception.SubscriptionExpiredException;
import com.curamatrix.hsm.exception.UserDeactivatedException;
import com.curamatrix.hsm.repository.TenantRepository;
import com.curamatrix.hsm.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AccessControlService accessControlService;
    private final LoginAttemptService loginAttemptService;

    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail();

        // ─── Brute-force protection: check if account is locked ─────
        if (loginAttemptService.isBlocked(email)) {
            long remainingSeconds = loginAttemptService.getRemainingLockSeconds(email);
            throw new BruteForceProtectionException(
                    "Too many failed login attempts. Your account is temporarily locked. " +
                    "Please try again after " + (remainingSeconds / 60 + 1) + " minute(s).");
        }

        // Verify user exists first to provide clear login errors
        User existingUser = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(email);
                    return new RuntimeException("Email is not correct");
                });

        // ─── Check if user is active ────────────────────────────────
        if (!Boolean.TRUE.equals(existingUser.getIsActive())) {
            throw new UserDeactivatedException();
        }

        // Authenticate user
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(email);
            throw new RuntimeException("Wrong password");
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(email);
            throw new RuntimeException("Authentication failed");
        }

        // ─── Successful authentication — clear brute-force tracker ─
        loginAttemptService.recordSuccess(email);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Derive tenant from the authenticated user
        User user = existingUser;
        Long tenantId = user.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));

        // Check if tenant is active
        if (!tenant.getIsActive()) {
            throw new RuntimeException("Hospital account is suspended. Please contact support.");
        }

        // Check subscription expiry
        if (tenant.getSubscriptionEnd().isBefore(LocalDate.now())) {
            throw new SubscriptionExpiredException(
                    "Subscription has expired. Please renew to continue using the service.");
        }

        return buildLoginResponse(user, tenant, userDetails);
    }

    /**
     * Refresh an existing valid JWT token — issues a fresh token with updated claims.
     * Validates that the user and tenant are still active and the subscription is valid.
     */
    public LoginResponse refreshToken(String currentToken) {
        // Extract username from existing token
        String username;
        try {
            username = jwtUtil.extractUsername(currentToken);
        } catch (Exception e) {
            throw new RuntimeException("Invalid or expired token. Please login again.");
        }

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", username));

        // Check if user is still active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UserDeactivatedException();
        }

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", user.getTenantId()));

        // Check if tenant is still active
        if (!tenant.getIsActive()) {
            throw new RuntimeException("Hospital account is suspended. Please contact support.");
        }

        // Check subscription expiry
        if (tenant.getSubscriptionEnd().isBefore(LocalDate.now())) {
            throw new SubscriptionExpiredException(
                    "Subscription has expired. Please renew to continue using the service.");
        }

        // Build a fresh UserDetails for token generation
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRoles().stream()
                        .map(r -> r.getName().name())
                        .toArray(String[]::new))
                .build();

        log.info("Token refreshed for user: {} from tenant: {}", user.getEmail(), tenant.getTenantKey());

        return buildLoginResponse(user, tenant, userDetails);
    }

    /**
     * Builds the LoginResponse with a fresh JWT token.
     */
    private LoginResponse buildLoginResponse(User user, Tenant tenant, UserDetails userDetails) {
        // Compute effective page keys (role pages + user extra pages)
        Set<String> effectivePageKeys = accessControlService.computeEffectivePageKeys(user);
        List<String> pageKeys = new ArrayList<>(effectivePageKeys);

        // Build authorities = role names for Spring Security hasRole() checks
        // RoleName enum already includes ROLE_ prefix (e.g. ROLE_RECEPTIONIST)
        List<String> authorities = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .toList();

        // Generate token with tenant + page claims
        String token = jwtUtil.generateToken(userDetails, tenant.getId(), tenant.getTenantKey(),
                authorities, pageKeys);

        String role = user.getRoles().stream()
                .findFirst()
                .map(r -> r.getName().name())
                .orElse("ROLE_USER");

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .role(role)
                .roles(authorities)
                .expiresIn(jwtUtil.getExpirationTime())
                .tenantId(tenant.getId())
                .tenantKey(tenant.getTenantKey())
                .hospitalName(tenant.getHospitalName())
                .subscriptionPlan(tenant.getSubscriptionPlan())
                .subscriptionExpiry(tenant.getSubscriptionEnd().toString())
                .pageKeys(pageKeys)
                .build();
    }
}
