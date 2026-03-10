package com.curamatrix.hsm.exception;

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(String message) {
        super(message);
    }

    public TenantNotFoundException() {
        super("Tenant not found or context not set");
    }
}
