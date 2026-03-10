package com.curamatrix.hsm.enums;

import lombok.Getter;

@Getter
public enum SubscriptionPlan {
    BASIC(99, 10, 1000, 5, 1000),
    STANDARD(499, 50, 10000, 50, 10000),
    PREMIUM(1999, -1, -1, 500, -1); // -1 means unlimited

    private final int monthlyPrice;
    private final int maxUsers;
    private final int maxPatients;
    private final int storageGB;
    private final int apiCallsPerHour;

    SubscriptionPlan(int monthlyPrice, int maxUsers, int maxPatients, int storageGB, int apiCallsPerHour) {
        this.monthlyPrice = monthlyPrice;
        this.maxUsers = maxUsers;
        this.maxPatients = maxPatients;
        this.storageGB = storageGB;
        this.apiCallsPerHour = apiCallsPerHour;
    }

    public boolean isUnlimited(String feature) {
        return switch (feature) {
            case "users" -> maxUsers == -1;
            case "patients" -> maxPatients == -1;
            case "apiCalls" -> apiCallsPerHour == -1;
            default -> false;
        };
    }
}
