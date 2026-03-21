package com.curamatrix.hsm.service;

import com.curamatrix.hsm.config.JwtUtil;
import com.curamatrix.hsm.dto.request.LoginRequest;
import com.curamatrix.hsm.dto.response.LoginResponse;
import com.curamatrix.hsm.entity.Tenant;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.exception.SubscriptionExpiredException;
import com.curamatrix.hsm.exception.TenantNotFoundException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public LoginResponse login(LoginRequest request) {
        // Validate tenant
        Tenant tenant = tenantRepository.findByTenantKey(request.getTenantKey())
                .orElseThrow(() -> new TenantNotFoundException("Invalid tenant key: " + request.getTenantKey()));

        // Check if tenant is active
        if (!tenant.getIsActive()) {
            throw new RuntimeException("Hospital account is suspended. Please contact support.");
        }

        // Check subscription expiry
        if (tenant.getSubscriptionEnd().isBefore(LocalDate.now())) {
            throw new SubscriptionExpiredException(
                    "Subscription has expired. Please renew to continue using the service.");
        }

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
        
        // Get user and verify tenant
        User user = existingUser;

        if (!user.getTenantId().equals(tenant.getId())) {
            throw new RuntimeException("User does not belong to this hospital");
        }

        // Generate token with tenant information
        String token = jwtUtil.generateToken(userDetails, tenant.getId(), tenant.getTenantKey());

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
                .build();
    }
}
