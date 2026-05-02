package com.curamatrix.hsm.enums;

public enum BillingItemType {
    // ── OPD ──────────────────────────────────────────────────────────────────
    CONSULTATION,
    REGISTRATION,

    // ── Diagnostics ───────────────────────────────────────────────────────────
    LAB,
    RADIOLOGY,

    // ── Pharmacy ─────────────────────────────────────────────────────────────
    MEDICINE,

    // ── Procedures / Surgery ─────────────────────────────────────────────────
    PROCEDURE,
    SURGERY,
    ANAESTHESIA,

    // ── IPD Daily Charges (auto-posted by BedChargeEngine) ───────────────────
    BED_CHARGE,
    ICU_CHARGE,
    NURSING_CHARGE,
    DIET_CHARGE,

    // ── IPD Clinical ─────────────────────────────────────────────────────────
    IPD_CONSULTATION,
    PHYSIOTHERAPY,

    // ── Financial ────────────────────────────────────────────────────────────
    DEPOSIT,
    INSURANCE_ADJUSTMENT,

    // ── Miscellaneous ────────────────────────────────────────────────────────
    OTHER
}
