package com.curamatrix.hsm.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility to generate BCrypt password hashes.
 * Run this class to generate hashes for your passwords.
 */
public class PasswordEncoderUtil {
    
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // Generate hashes for common passwords
        System.out.println("=== BCrypt Password Generator ===\n");
        
        String[] passwords = {"admin123", "doctor123", "reception123", "password123"};
        
        for (String password : passwords) {
            String hash = encoder.encode(password);
            System.out.println("Password: " + password);
            System.out.println("BCrypt Hash: " + hash);
            System.out.println();
        }
        
        // If you want to generate a specific password, uncomment below:
        // String customPassword = "your_password_here";
        // System.out.println("Custom Password: " + customPassword);
        // System.out.println("BCrypt Hash: " + encoder.encode(customPassword));
    }
}
