package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.access.UserAccessResponse;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.repository.UserRepository;
import com.curamatrix.hsm.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;
    private final AccessControlService accessControlService;

    @GetMapping("/access")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserAccessResponse> getMyAccess(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return ResponseEntity.ok(accessControlService.getUserAccess(user.getId()));
    }
}
