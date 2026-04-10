package com.curamatrix.hsm.dto.response;

import com.curamatrix.hsm.enums.DoctorStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingContextResponse {
    private Long patientId;
    private CasePaperStatus casePaper;
    private List<DepartmentWithDoctors> departments;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CasePaperStatus {
        private boolean valid;
        private LocalDateTime expiresAt;
        private Long registrationId;
        private int remainingDays;
        private boolean expiringSoon;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DepartmentWithDoctors {
        private Long departmentId;
        private String departmentName;
        private List<DoctorInfo> doctors;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DoctorInfo {
        private Long doctorId;
        private String doctorName;
        private String qualification;
        private BigDecimal consultationFee;
        private boolean presentToday;
        private DoctorStatus status;
        private String statusNote;
        private int activeQueueLength;
    }
}
