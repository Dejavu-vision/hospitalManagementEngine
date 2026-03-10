package com.curamatrix.hsm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CuraMatrix Hospital Management System API")
                        .version("1.0.0")
                        .description("""
                                # CuraMatrix HSM REST API Documentation
                                
                                A comprehensive Hospital Management System providing REST APIs for:
                                - **Patient Management** - Register and manage patient records
                                - **Appointment System** - Scheduled appointments and walk-in queue
                                - **Diagnosis & Prescriptions** - Doctor workflow for patient care
                                - **Medicine Search** - Fast autocomplete search
                                - **User Management** - Admin controls for users and roles
                                - **Department Management** - Hospital organization
                                
                                ## Authentication
                                
                                All endpoints (except `/api/auth/login`) require JWT authentication.
                                
                                ### How to authenticate:
                                1. Click **Authorize** button (🔒) at the top
                                2. Login via `/api/auth/login` endpoint first
                                3. Copy the `token` from the response
                                4. Paste it in the **Value** field (without "Bearer" prefix)
                                5. Click **Authorize** and **Close**
                                
                                ### Default Credentials:
                                - **Admin**: admin@curamatrix.com / admin123
                                - **Doctor**: doctor@curamatrix.com / doctor123
                                - **Receptionist**: reception@curamatrix.com / reception123
                                
                                ## Role-Based Access
                                
                                | Role | Access |
                                |------|--------|
                                | ADMIN | User management, all read operations |
                                | DOCTOR | Diagnosis, prescriptions, patient queue |
                                | RECEPTIONIST | Patient registration, appointments, billing |
                                
                                ## Quick Start
                                
                                1. **Login** - Use `/api/auth/login` to get JWT token
                                2. **Authorize** - Click 🔒 button and paste token
                                3. **Test APIs** - Try endpoints based on your role
                                
                                ## Support
                                
                                For issues: support@curamatrix.com
                                """)
                        .contact(new Contact()
                                .name("CuraMatrix Support")
                                .email("support@curamatrix.com")
                                .url("https://curamatrix.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://curamatrix.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.curamatrix.com")
                                .description("Production Server (if deployed)")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token obtained from /api/auth/login endpoint")));
    }
}
