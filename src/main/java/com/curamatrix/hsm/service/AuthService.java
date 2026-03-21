package com.curamatrix.hsm.service;

import com.curamatrix.hsm.config.JwtUtil;
import com.curamatrix.hsm.dto.request.LoginRequest;
import com.curamatrix.hsm.dto.response.LoginResponse;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.entity.Role;
import com.curamatrix.hsm.exception.SubscriptionExpiredException;
import com.curamatrix.hsm.repository.TenantRepository;
import com.curamatrix.hsm.repository.UserRepository;
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

    public LoginResponse login(LoginRequest request) {
        // Verify user exists first to provide clear login errors
        User existingUser = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email is not correct"));

        // Authenticate user
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new RuntimeException("Wrong password");
        } catch (AuthenticationException e) {
            throw new RuntimeException("Authentication failed");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Derive tenant from the authenticated user
        User user = existingUser;
        Long tenantId = user.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found for user"));

        // Check if tenant is active
        if (!tenant.getIsActive()) {
            throw new RuntimeException("Hospital account is suspended. Please contact support.");
        }

        // Check subscription expiry
        if (tenant.getSubscriptionEnd().isBefore(LocalDate.now())) {
            throw new SubscriptionExpiredException(
                    "Subscription has expired. Please renew to continue using the service.");
        }

        // Compute effective page keys (role pages + user extra pages)
        Set<String> effectivePageKeys = accessControlService.computeEffectivePageKeys(user);
        List<String> pageKeys = new ArrayList<>(effectivePageKeys);

        // Build authorities = role names only (no permissions in JWT)
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

        log.info("User logged in: {} from tenant: {}", user.getEmail(), tenant.getTenantKey());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .role(role)
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
