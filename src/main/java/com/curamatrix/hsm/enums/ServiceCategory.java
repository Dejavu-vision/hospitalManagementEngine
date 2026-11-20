package com.curamatrix.hsm.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ServiceCategory {
    IMAGING,        // X-Ray, MRI, CT Scan, Ultrasound
    BLOOD_TEST,     // CBC, Blood Sugar, Lipid Profile
    DIAGNOSTIC,     // ECG, EEG, EMG
    PATHOLOGY,      // Biopsy, Cytology
    MICROBIOLOGY,   // Culture, Sensitivity
    BIOCHEMISTRY,   // Enzymes, Hormones, Electrolytes
    HEMATOLOGY,     // Coagulation, Hemogram
    CARDIOLOGY,     // Echo, TMT, ECG, Holter
    RADIOLOGY,      // Special X-Ray, Fluoroscopy, CT
    SEROLOGY,       // Antibodies, Antigens, ELISA
    HISTOPATHOLOGY, // Tissue biopsy, Frozen sections
    MOLECULAR_DIAGNOSTICS, // PCR, RT-PCR, Genetics
    URINALYSIS,     // Urine routine, Microscopy
    CYTOLOGY,       // Pap smear, FNAC
    ENDOCRINOLOGY,  // Thyroid, Growth hormones
    NEUROLOGY,      // NCS, BERA, VEP
    OTHER;          // Custom or other clinical services

    @JsonCreator
    public static ServiceCategory fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return OTHER;
        }
        String normalized = value.trim().toUpperCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace("/", "_");
        for (ServiceCategory sc : values()) {
            if (sc.name().equalsIgnoreCase(normalized)) {
                return sc;
            }
        }
        return OTHER;
    }
}

