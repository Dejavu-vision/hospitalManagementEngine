package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.BlockTokenRequest;
import com.curamatrix.hsm.dto.response.BlockedTokenResponse;
import com.curamatrix.hsm.entity.BlockedToken;
import com.curamatrix.hsm.entity.BlockedToken.BlockedTokenStatus;
import com.curamatrix.hsm.entity.User;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.BlockedTokenRepository;
import com.curamatrix.hsm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockedTokenService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final BlockedTokenRepository blockedTokenRepository;
    private final UserRepository userRepository;

    // ── List today's blocked tokens ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BlockedTokenResponse> getTodayBlockedTokens(Long doctorId) {
        Long tenantId = TenantContext.getTenantId();
        return blockedTokenRepository
                .findByAppointmentDateAndTenantIdAndDoctorIdOrderByTokenNumberAsc(
                        LocalDate.now(), tenantId, doctorId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BlockedTokenResponse> getAvailableBlockedTokens(Long doctorId) {
        Long tenantId = TenantContext.getTenantId();
        return blockedTokenRepository
                .findByAppointmentDateAndTenantIdAndDoctorIdAndStatusOrderByTokenNumberAsc(
                        LocalDate.now(), tenantId, doctorId, BlockedTokenStatus.BLOCKED)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Block a token ─────────────────────────────────────────────────────────

    @Transactional
    public BlockedTokenResponse blockToken(BlockTokenRequest request) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        Long doctorId = request.getDoctorId();

        // Check if already blocked for this doctor today
        blockedTokenRepository.findByTokenNumberAndAppointmentDateAndTenantIdAndDoctorId(
                request.getTokenNumber(), today, tenantId, doctorId)
                .ifPresent(bt -> {
                    if (bt.getStatus() == BlockedTokenStatus.BLOCKED) {
                        throw new DuplicateResourceException(
                                "Token T-" + String.format("%03d", request.getTokenNumber()) +
                                " is already blocked for this doctor today");
                    }
                });

        User currentUser = getCurrentUser();

        BlockedToken bt = BlockedToken.builder()
                .tokenNumber(request.getTokenNumber())
                .appointmentDate(today)
                .doctorId(doctorId)
                .status(BlockedTokenStatus.BLOCKED)
                .reason(request.getReason())
                .blockedBy(currentUser)
                .build();
        bt.setTenantId(tenantId);

        bt = blockedTokenRepository.save(bt);
        log.info("Token {} blocked by {} for doctor {} on {}", request.getTokenNumber(),
                currentUser.getFullName(), doctorId, today);
        return toResponse(bt);
    }

    // ── Release a blocked token ───────────────────────────────────────────────

    @Transactional
    public BlockedTokenResponse releaseToken(Integer tokenNumber, Long doctorId) {
        Long tenantId = TenantContext.getTenantId();
        BlockedToken bt = blockedTokenRepository
                .findByTokenNumberAndAppointmentDateAndTenantIdAndDoctorIdAndStatus(
                        tokenNumber, LocalDate.now(), tenantId, doctorId, BlockedTokenStatus.BLOCKED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BlockedToken", "tokenNumber", tokenNumber));

        bt.setStatus(BlockedTokenStatus.RELEASED);
        bt = blockedTokenRepository.save(bt);
        log.info("Token {} released for doctor {}", tokenNumber, doctorId);
        return toResponse(bt);
    }

    // ── Assign a blocked token to an appointment ──────────────────────────────

    // Uses REQUIRES_NEW so this commits independently — the blocked token record
    // is updated even if the outer createWalkIn transaction has issues.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void assignBlockedToken(Integer tokenNumber, Long appointmentId, Long tenantId, Long doctorId) {
        blockedTokenRepository
                .findByTokenNumberAndAppointmentDateAndTenantIdAndDoctorIdAndStatus(
                        tokenNumber, LocalDate.now(), tenantId, doctorId, BlockedTokenStatus.BLOCKED)
                .ifPresent(bt -> {
                    bt.setStatus(BlockedTokenStatus.ASSIGNED);
                    bt.setAssignedToAppointmentId(appointmentId);
                    bt.setAssignedAt(LocalDateTime.now());
                    blockedTokenRepository.save(bt);
                    log.info("Blocked token {} assigned to appointment {} for doctor {}",
                            tokenNumber, appointmentId, doctorId);
                });
    }

    // ── Check if a token number is blocked for a specific doctor ─────────────

    // Note: No @Transactional here — this is a simple read called from within
    // createWalkIn's @Transactional. Adding readOnly=true would mark the outer
    // write transaction as read-only, causing a rollback-only error.
    public boolean isTokenBlocked(Integer tokenNumber, Long tenantId, Long doctorId) {
        return blockedTokenRepository.isTokenBlocked(tokenNumber, LocalDate.now(), tenantId, doctorId);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private BlockedTokenResponse toResponse(BlockedToken bt) {
        return BlockedTokenResponse.builder()
                .id(bt.getId())
                .tokenNumber(bt.getTokenNumber())
                .tokenDisplay("T-" + String.format("%03d", bt.getTokenNumber()))
                .appointmentDate(bt.getAppointmentDate())
                .doctorId(bt.getDoctorId())
                .status(bt.getStatus().name())
                .reason(bt.getReason())
                .blockedByName(bt.getBlockedBy() != null ? bt.getBlockedBy().getFullName() : null)
                .blockedAt(bt.getBlockedAt() != null ? bt.getBlockedAt().format(TIME_FMT) : null)
                .assignedToAppointmentId(bt.getAssignedToAppointmentId())
                .assignedAt(bt.getAssignedAt() != null ? bt.getAssignedAt().format(TIME_FMT) : null)
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
