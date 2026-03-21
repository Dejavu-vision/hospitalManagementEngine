package com.curamatrix.hsm.controller;

import com.curamatrix.hsm.dto.request.LoginRequest;
import com.curamatrix.hsm.dto.response.LoginResponse;
import com.curamatrix.hsm.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication", description = "Login and JWT token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(
        summary = "User Login",
        description = "Authenticate using email and password. Tenant is auto-detected from the user's account.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Login successful, JWT token returned"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
        }
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "Login credentials",
        required = true,
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "Admin Login",
                    value = "{\"email\": \"admin@curamatrix.com\", \"password\": \"admin123\"}"
                ),
                @ExampleObject(
                    name = "Doctor Login",
                    value = "{\"email\": \"doctor@curamatrix.com\", \"password\": \"doctor123\"}"
                ),
                @ExampleObject(
                    name = "Receptionist Login",
                    value = "{\"email\": \"reception@curamatrix.com\", \"password\": \"reception123\"}"
                )
            }
        )
    )
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
}
