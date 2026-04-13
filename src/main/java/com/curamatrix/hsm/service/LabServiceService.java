package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.LabServiceRequest;
import com.curamatrix.hsm.dto.request.PricingTierRequest;
import com.curamatrix.hsm.dto.response.LabServiceResponse;
import com.curamatrix.hsm.dto.response.LabServiceSearchResponse;
import com.curamatrix.hsm.dto.response.PricingTierResponse;
import com.curamatrix.hsm.entity.LabService;
import com.curamatrix.hsm.entity.ServicePricingTier;
import com.curamatrix.hsm.enums.ServiceCategory;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.LabServiceRepository;
import com.curamatrix.hsm.repository.LabTestRepository;
import com.curamatrix.hsm.repository.ServicePricingTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabServiceService {

    private final LabServiceRepository labServiceRepository;
    private final ServicePricingTierRepository servicePricingTierRepository;
    private final LabTestRepository labTestRepository;

    // ==================== Lab Service CRUD ====================

    @Transactional
    public LabServiceResponse createLabService(LabServiceRequest request) {
        Long tenantId = TenantContext.getTenantId();

        labServiceRepository.findByServiceCodeAndTenantId(request.getServiceCode(), tenantId)
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("LabService", "serviceCode", request.getServiceCode());
                });

        LabService labService = LabService.builder()
                .serviceName(request.getServiceName())
                .serviceCode(request.getServiceCode())
                .category(request.getCategory())
                .description(request.getDescription())
                .defaultPrice(request.getDefaultPrice())
                .active(true)
                .build();
        labService.setTenantId(tenantId);

        labService = labServiceRepository.save(labService);
        log.info("Created lab service: id={}, code={}, tenantId={}", labService.getId(), labService.getServiceCode(), tenantId);
        return mapToResponse(labService);
    }

    @Transactional
    public LabServiceResponse updateLabService(Long id, LabServiceRequest request) {
        Long tenantId = TenantContext.getTenantId();

        LabService labService = labServiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabService", "id", id));

        // Check if service code is being changed to one that already exists
        if (!labService.getServiceCode().equals(request.getServiceCode())) {
            labServiceRepository.findByServiceCodeAndTenantId(request.getServiceCode(), tenantId)
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("LabService", "serviceCode", request.getServiceCode());
                    });
        }

        labService.setServiceName(request.getServiceName());
        labService.setServiceCode(request.getServiceCode());
        labService.setCategory(request.getCategory());
        labService.setDescription(request.getDescription());
        labService.setDefaultPrice(request.getDefaultPrice());

        labService = labServiceRepository.save(labService);
        log.info("Updated lab service: id={}, tenantId={}", id, tenantId);
        return mapToResponse(labService);
    }

    @Transactional
    public LabServiceResponse deactivateLabService(Long id) {
        Long tenantId = TenantContext.getTenantId();

        LabService labService = labServiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabService", "id", id));

        labService.setActive(false);
        labService = labServiceRepository.save(labService);
        log.info("Deactivated lab service: id={}, tenantId={}", id, tenantId);
        return mapToResponse(labService);
    }

    @Transactional(readOnly = true)
    public Page<LabServiceResponse> listLabServices(ServiceCategory category, Boolean active, Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();

        Page<LabService> page;
        if (category != null && active != null) {
            page = labServiceRepository.findByTenantIdAndCategoryAndActive(tenantId, category, active, pageable);
        } else if (category != null) {
            page = labServiceRepository.findByTenantIdAndCategory(tenantId, category, pageable);
        } else if (active != null) {
            page = labServiceRepository.findByTenantIdAndActive(tenantId, active, pageable);
        } else {
            page = labServiceRepository.findByTenantId(tenantId, pageable);
        }

        return page.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<LabServiceSearchResponse> searchLabServices(String query) {
        Long tenantId = TenantContext.getTenantId();

        if (query == null || query.trim().length() < 2) {
            return Collections.emptyList();
        }

        List<LabService> results = labServiceRepository.searchActiveByNameOrCode(
                tenantId, query.trim(), Pageable.ofSize(20));

        return results.stream()
                .map(this::mapToSearchResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LabServiceResponse getLabServiceById(Long id) {
        Long tenantId = TenantContext.getTenantId();

        LabService labService = labServiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabService", "id", id));

        return mapToResponse(labService);
    }

    // ==================== Pricing Tier Management ====================

    @Transactional
    public PricingTierResponse addPricingTier(Long labServiceId, PricingTierRequest request) {
        Long tenantId = TenantContext.getTenantId();

        LabService labService = labServiceRepository.findByIdAndTenantId(labServiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabService", "id", labServiceId));

        if (request.getValidFrom().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Valid-from date cannot be in the past");
        }

        if (request.getValidTo() != null && !request.getValidTo().isAfter(request.getValidFrom())) {
            throw new IllegalArgumentException("Valid-to date must be after valid-from date");
        }

        ServicePricingTier tier = ServicePricingTier.builder()
                .labService(labService)
                .tierName(request.getTierName())
                .price(request.getPrice())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .build();
        tier.setTenantId(tenantId);

        tier = servicePricingTierRepository.save(tier);
        log.info("Added pricing tier: id={}, labServiceId={}, tenantId={}", tier.getId(), labServiceId, tenantId);
        return mapToTierResponse(tier);
    }

    @Transactional
    public PricingTierResponse updatePricingTier(Long labServiceId, Long tierId, PricingTierRequest request) {
        Long tenantId = TenantContext.getTenantId();

        // Verify the lab service exists for this tenant
        labServiceRepository.findByIdAndTenantId(labServiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabService", "id", labServiceId));

        ServicePricingTier tier = servicePricingTierRepository.findByIdAndTenantId(tierId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ServicePricingTier", "id", tierId));

        tier.setTierName(request.getTierName());
        tier.setPrice(request.getPrice());
        tier.setValidFrom(request.getValidFrom());
        tier.setValidTo(request.getValidTo());

        tier = servicePricingTierRepository.save(tier);
        log.info("Updated pricing tier: id={}, labServiceId={}, tenantId={}", tierId, labServiceId, tenantId);
        return mapToTierResponse(tier);
    }

    @Transactional
    public void deletePricingTier(Long labServiceId, Long tierId) {
        Long tenantId = TenantContext.getTenantId();

        // Verify the lab service exists for this tenant
        labServiceRepository.findByIdAndTenantId(labServiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabService", "id", labServiceId));

        ServicePricingTier tier = servicePricingTierRepository.findByIdAndTenantId(tierId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ServicePricingTier", "id", tierId));

        // Check if any LabTest exists for this service (simplified check)
        if (labTestRepository.existsByLabServiceId(labServiceId)) {
            throw new DuplicateResourceException(
                    "Cannot delete pricing tier: lab tests exist for this service");
        }

        servicePricingTierRepository.delete(tier);
        log.info("Deleted pricing tier: id={}, labServiceId={}, tenantId={}", tierId, labServiceId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<PricingTierResponse> listPricingTiers(Long labServiceId) {
        Long tenantId = TenantContext.getTenantId();

        // Verify the lab service exists for this tenant
        labServiceRepository.findByIdAndTenantId(labServiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabService", "id", labServiceId));

        return servicePricingTierRepository
                .findByLabServiceIdAndTenantIdOrderByValidFromDesc(labServiceId, tenantId)
                .stream()
                .map(this::mapToTierResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal resolvePrice(Long labServiceId, Long tenantId) {
        List<ServicePricingTier> activeTiers = servicePricingTierRepository
                .findActiveTiers(labServiceId, tenantId, LocalDate.now());

        if (!activeTiers.isEmpty()) {
            return activeTiers.get(0).getPrice();
        }

        LabService labService = labServiceRepository.findByIdAndTenantId(labServiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("LabService", "id", labServiceId));

        return labService.getDefaultPrice();
    }

    // ==================== Mapping Helpers ====================

    private LabServiceResponse mapToResponse(LabService labService) {
        Long tenantId = labService.getTenantId();
        BigDecimal currentPrice = resolveCurrentPrice(labService, tenantId);

        List<PricingTierResponse> tierResponses = labService.getPricingTiers() != null
                ? labService.getPricingTiers().stream()
                    .map(this::mapToTierResponse)
                    .collect(Collectors.toList())
                : Collections.emptyList();

        return LabServiceResponse.builder()
                .id(labService.getId())
                .serviceName(labService.getServiceName())
                .serviceCode(labService.getServiceCode())
                .category(labService.getCategory())
                .description(labService.getDescription())
                .defaultPrice(labService.getDefaultPrice())
                .currentPrice(currentPrice)
                .active(labService.isActive())
                .pricingTiers(tierResponses)
                .build();
    }

    private LabServiceSearchResponse mapToSearchResponse(LabService labService) {
        Long tenantId = labService.getTenantId();
        BigDecimal currentPrice = resolveCurrentPrice(labService, tenantId);

        return LabServiceSearchResponse.builder()
                .id(labService.getId())
                .serviceName(labService.getServiceName())
                .serviceCode(labService.getServiceCode())
                .category(labService.getCategory())
                .currentPrice(currentPrice)
                .build();
    }

    private PricingTierResponse mapToTierResponse(ServicePricingTier tier) {
        return PricingTierResponse.builder()
                .id(tier.getId())
                .tierName(tier.getTierName())
                .price(tier.getPrice())
                .validFrom(tier.getValidFrom())
                .validTo(tier.getValidTo())
                .build();
    }

    private BigDecimal resolveCurrentPrice(LabService labService, Long tenantId) {
        List<ServicePricingTier> activeTiers = servicePricingTierRepository
                .findActiveTiers(labService.getId(), tenantId, LocalDate.now());

        if (!activeTiers.isEmpty()) {
            return activeTiers.get(0).getPrice();
        }
        return labService.getDefaultPrice();
    }
}
