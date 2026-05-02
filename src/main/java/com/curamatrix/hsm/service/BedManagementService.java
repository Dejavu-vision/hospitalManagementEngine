package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.BedRequest;
import com.curamatrix.hsm.dto.request.RoomRequest;
import com.curamatrix.hsm.dto.request.WardRequest;
import com.curamatrix.hsm.dto.response.BedResponse;
import com.curamatrix.hsm.dto.response.RoomResponse;
import com.curamatrix.hsm.dto.response.WardResponse;
import com.curamatrix.hsm.entity.Bed;
import com.curamatrix.hsm.entity.Room;
import com.curamatrix.hsm.entity.Ward;
import com.curamatrix.hsm.enums.BedStatus;
import com.curamatrix.hsm.enums.BedType;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.BedRepository;
import com.curamatrix.hsm.repository.RoomRepository;
import com.curamatrix.hsm.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BedManagementService {

    private final WardRepository wardRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    // --- Ward Logic ---
    
    @Transactional
    public WardResponse createWard(WardRequest request) {
        Long tenantId = TenantContext.getTenantId();
        
        // Optional uniqueness check (omitted for brevity, handled by DB constraint)
        Ward ward = Ward.builder()
                .name(request.getName())
                .floor(request.getFloor())
                .description(request.getDescription())
                .build();
        ward.setTenantId(tenantId);
        ward = wardRepository.save(ward);
        return mapToResponse(ward, false);
    }

    public List<WardResponse> getAllWards(boolean includeHierarchy) {
        Long tenantId = TenantContext.getTenantId();
        return wardRepository.findByTenantId(tenantId).stream()
                .map(w -> mapToResponse(w, includeHierarchy))
                .collect(Collectors.toList());
    }

    // --- Room Logic ---

    @Transactional
    public RoomResponse createRoom(RoomRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Ward ward = wardRepository.findById(request.getWardId())
                .orElseThrow(() -> new ResourceNotFoundException("Ward", "id", request.getWardId()));

        Room room = Room.builder()
                .roomNumber(request.getRoomNumber())
                .ward(ward)
                .roomType(request.getRoomType())
                .amenities(request.getAmenities())
                .build();
        room.setTenantId(tenantId);
        room = roomRepository.save(room);
        return mapToResponse(room, false);
    }

    public List<RoomResponse> getRoomsByWard(Long wardId) {
        Long tenantId = TenantContext.getTenantId();
        return roomRepository.findByWardIdAndTenantId(wardId, tenantId).stream()
                .map(r -> mapToResponse(r, false))
                .collect(Collectors.toList());
    }

    // --- Bed Logic ---

    @Transactional
    public BedResponse createBed(BedRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", request.getRoomId()));

        Bed bed = Bed.builder()
                .bedNumber(request.getBedNumber())
                .room(room)
                .status(request.getStatus())
                .dailyPrice(request.getDailyPrice())
                .build();
        bed.setTenantId(tenantId);
        bed = bedRepository.save(bed);
        return mapToResponse(bed);
    }

    @Transactional
    public BedResponse updateBedStatus(Long id, com.curamatrix.hsm.enums.BedStatus status) {
        Bed bed = bedRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", "id", id));
        bed.setStatus(status);
        return mapToResponse(bedRepository.save(bed));
    }

    @Transactional(readOnly = true)
    public List<BedResponse> getAvailableBeds(Long wardId, com.curamatrix.hsm.enums.BedType roomType) {
        Long tenantId = TenantContext.getTenantId();
        return bedRepository.findAvailableBeds(tenantId, wardId, roomType)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // --- Stats ---

    @Transactional(readOnly = true)
    public Map<String, Object> getBedStats() {
        Long tenantId = TenantContext.getTenantId();
        List<Bed> allBeds = bedRepository.findByTenantId(tenantId);
        List<Ward> allWards = wardRepository.findByTenantId(tenantId);

        long total = allBeds.size();
        long available = allBeds.stream().filter(b -> b.getStatus() == BedStatus.AVAILABLE).count();
        long occupied = allBeds.stream().filter(b -> b.getStatus() == BedStatus.OCCUPIED).count();
        long cleaning = allBeds.stream().filter(b -> b.getStatus() == BedStatus.CLEANING).count();
        long maintenance = allBeds.stream().filter(b -> b.getStatus() == BedStatus.MAINTENANCE).count();

        // Ward breakdown
        List<Map<String, Object>> wardBreakdown = new ArrayList<>();
        for (Ward ward : allWards) {
            List<Bed> wardBeds = allBeds.stream()
                    .filter(b -> b.getRoom().getWard().getId().equals(ward.getId()))
                    .collect(Collectors.toList());

            long wTotal = wardBeds.size();
            long wAvail = wardBeds.stream().filter(b -> b.getStatus() == BedStatus.AVAILABLE).count();
            long wOccup = wardBeds.stream().filter(b -> b.getStatus() == BedStatus.OCCUPIED).count();
            long wClean = wardBeds.stream().filter(b -> b.getStatus() == BedStatus.CLEANING).count();
            long wMaint = wardBeds.stream().filter(b -> b.getStatus() == BedStatus.MAINTENANCE).count();

            long roomCount = roomRepository.findByWardIdAndTenantId(ward.getId(), tenantId).size();

            Map<String, Object> wd = new LinkedHashMap<>();
            wd.put("wardId", ward.getId());
            wd.put("wardName", ward.getName());
            wd.put("floor", ward.getFloor());
            wd.put("rooms", roomCount);
            wd.put("total", wTotal);
            wd.put("available", wAvail);
            wd.put("occupied", wOccup);
            wd.put("cleaning", wClean);
            wd.put("maintenance", wMaint);
            wardBreakdown.add(wd);
        }

        // Room-type breakdown
        Map<String, List<Bed>> byType = allBeds.stream()
                .collect(Collectors.groupingBy(b -> b.getRoom().getRoomType().name()));

        List<Map<String, Object>> roomTypeBreakdown = new ArrayList<>();
        for (Map.Entry<String, List<Bed>> entry : byType.entrySet()) {
            List<Bed> typeBeds = entry.getValue();
            long tAvail = typeBeds.stream().filter(b -> b.getStatus() == BedStatus.AVAILABLE).count();
            long tOccup = typeBeds.stream().filter(b -> b.getStatus() == BedStatus.OCCUPIED).count();
            BigDecimal avgPrice = typeBeds.stream()
                    .map(Bed::getDailyPrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!typeBeds.isEmpty()) {
                avgPrice = avgPrice.divide(BigDecimal.valueOf(typeBeds.size()), 2, RoundingMode.HALF_UP);
            }

            Map<String, Object> td = new LinkedHashMap<>();
            td.put("roomType", entry.getKey());
            td.put("total", (long) typeBeds.size());
            td.put("available", tAvail);
            td.put("occupied", tOccup);
            td.put("avgDailyPrice", avgPrice);
            roomTypeBreakdown.add(td);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalBeds", total);
        stats.put("available", available);
        stats.put("occupied", occupied);
        stats.put("cleaning", cleaning);
        stats.put("maintenance", maintenance);
        stats.put("totalWards", (long) allWards.size());
        stats.put("wardBreakdown", wardBreakdown);
        stats.put("roomTypeBreakdown", roomTypeBreakdown);
        return stats;
    }

    // --- Mapping ---

    private WardResponse mapToResponse(Ward ward, boolean includeHierarchy) {
        WardResponse.WardResponseBuilder builder = WardResponse.builder()
                .id(ward.getId())
                .name(ward.getName())
                .floor(ward.getFloor())
                .description(ward.getDescription());

        if (includeHierarchy) {
            Long tenantId = TenantContext.getTenantId();
            List<RoomResponse> rooms = roomRepository.findByWardIdAndTenantId(ward.getId(), tenantId)
                    .stream()
                    .map(r -> mapToResponse(r, true))
                    .collect(Collectors.toList());
            builder.rooms(rooms);
        }
        return builder.build();
    }

    private RoomResponse mapToResponse(Room room, boolean includeHierarchy) {
        RoomResponse.RoomResponseBuilder builder = RoomResponse.builder()
                .id(room.getId())
                .roomNumber(room.getRoomNumber())
                .wardId(room.getWard().getId())
                .wardName(room.getWard().getName())
                .roomType(room.getRoomType())
                .amenities(room.getAmenities());

        if (includeHierarchy) {
            Long tenantId = TenantContext.getTenantId();
            List<BedResponse> beds = bedRepository.findByRoomIdAndTenantId(room.getId(), tenantId)
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            builder.beds(beds);
        }
        return builder.build();
    }

    private BedResponse mapToResponse(Bed bed) {
        return BedResponse.builder()
                .id(bed.getId())
                .bedNumber(bed.getBedNumber())
                .roomId(bed.getRoom().getId())
                .roomNumber(bed.getRoom().getRoomNumber())
                .wardId(bed.getRoom().getWard().getId())
                .wardName(bed.getRoom().getWard().getName())
                .status(bed.getStatus())
                .dailyPrice(bed.getDailyPrice())
                .build();
    }
}
