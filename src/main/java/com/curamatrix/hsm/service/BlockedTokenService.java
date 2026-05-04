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
    public List<BlockedTokenResponse> getTodayBlockedTokens() {
        Long tenantId = TenantContext.getTenantId();
        return blockedTokenRepository
                .findByAppointmentDateAndTenantIdOrderByTokenNumberAsc(LocalDate.now(), tenantId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BlockedTokenResponse> getAvailableBlockedTokens() {
        Long tenantId = TenantContext.getTenantId();
        return blockedTokenRepository
                .findByAppointmentDateAndTenantIdAndStatusOrderByTokenNumberAsc(
                        LocalDate.now(), tenantId, BlockedTokenStatus.BLOCKED)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Block a token ─────────────────────────────────────────────────────────

    @Transactional
    public BlockedTokenResponse blockToken(BlockTokenRequest request) {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();

        // Check if already blocked
        blockedTokenRepository.findByTokenNumberAndAppointmentDateAndTenantId(
                request.getTokenNumber(), today, tenantId)
                .ifPresent(bt -> {
                    if (bt.getStatus() == BlockedTokenStatus.BLOCKED) {
                        throw new DuplicateResourceException(
                                "Token T-" + String.format("%03d", request.getTokenNumber()) +
                                " is already blocked for today");
                    }
                });

        User currentUser = getCurrentUser();

        BlockedToken bt = BlockedToken.builder()
                .tokenNumber(request.getTokenNumber())
                .appointmentDate(today)
                .status(BlockedTokenStatus.BLOCKED)
                .reason(request.getReason())
                .blockedBy(currentUser)
                .build();
        bt.setTenantId(tenantId);

        bt = blockedTokenRepository.save(bt);
        log.info("Token {} blocked by {} for {}", request.getTokenNumber(), currentUser.getFullName(), today);
        return toResponse(bt);
    }

    // ── Release a blocked token ───────────────────────────────────────────────

    @Transactional
    public BlockedTokenResponse releaseToken(Integer tokenNumber) {
        Long tenantId = TenantContext.getTenantId();
        BlockedToken bt = blockedTokenRepository
                .findByTokenNumberAndAppointmentDateAndTenantIdAndStatus(
                        tokenNumber, LocalDate.now(), tenantId, BlockedTokenStatus.BLOCKED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BlockedToken", "tokenNumber", tokenNumber));

        bt.setStatus(BlockedTokenStatus.RELEASED);
        bt = blockedTokenRepository.save(bt);
        log.info("Token {} released", tokenNumber);
        return toResponse(bt);
    }

    // ── Assign a blocked token to an appointment ──────────────────────────────

    // Uses REQUIRES_NEW so this commits independently — the blocked token record
    // is updated even if the outer createWalkIn transaction has issues.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void assignBlockedToken(Integer tokenNumber, Long appointmentId, Long tenantId) {
        blockedTokenRepository
                .findByTokenNumberAndAppointmentDateAndTenantIdAndStatus(
                        tokenNumber, LocalDate.now(), tenantId, BlockedTokenStatus.BLOCKED)
                .ifPresent(bt -> {
                    bt.setStatus(BlockedTokenStatus.ASSIGNED);
                    bt.setAssignedToAppointmentId(appointmentId);
                    bt.setAssignedAt(LocalDateTime.now());
                    blockedTokenRepository.save(bt);
                    log.info("Blocked token {} assigned to appointment {}", tokenNumber, appointmentId);
                });
    }

    // ── Check if a token number is blocked (used during auto-increment) ───────

    // Note: No @Transactional here — this is a simple read called from within
    // createWalkIn's @Transactional. Adding readOnly=true would mark the outer
    // write transaction as read-only, causing a rollback-only error.
    public boolean isTokenBlocked(Integer tokenNumber, Long tenantId) {
        return blockedTokenRepository.isTokenBlocked(tokenNumber, LocalDate.now(), tenantId);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private BlockedTokenResponse toResponse(BlockedToken bt) {
        return BlockedTokenResponse.builder()
                .id(bt.getId())
                .tokenNumber(bt.getTokenNumber())
                .tokenDisplay("T-" + String.format("%03d", bt.getTokenNumber()))
                .appointmentDate(bt.getAppointmentDate())
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
