package com.curamatrix.hsm.service;

import com.curamatrix.hsm.context.TenantContext;
import com.curamatrix.hsm.dto.request.HospitalServiceRequest;
import com.curamatrix.hsm.dto.response.HospitalServiceResponse;
import com.curamatrix.hsm.entity.Department;
import com.curamatrix.hsm.entity.HospitalService;
import com.curamatrix.hsm.enums.BillingItemType;
import com.curamatrix.hsm.exception.DuplicateResourceException;
import com.curamatrix.hsm.exception.ResourceNotFoundException;
import com.curamatrix.hsm.repository.DepartmentRepository;
import com.curamatrix.hsm.repository.HospitalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HospitalServiceCatalogService {

    private final HospitalServiceRepository hospitalServiceRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<HospitalServiceResponse> getAllServices(Long departmentId, BillingItemType type, Boolean active) {
        Long tenantId = TenantContext.getTenantId();
        
        List<HospitalService> services;
        if (departmentId != null) {
            services = hospitalServiceRepository.findAllByTenantIdAndDepartmentId(tenantId, departmentId);
        } else {
            services = hospitalServiceRepository.findAllByTenantId(tenantId);
        }

        return services.stream()
                .filter(s -> type == null || s.getItemType() == type)
                .filter(s -> active == null || s.isActive() == active)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HospitalServiceResponse getServiceById(Long id) {
        Long tenantId = TenantContext.getTenantId();
        HospitalService service = hospitalServiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("HospitalService", "id", id));
        return mapToResponse(service);
    }

    @Transactional
    public HospitalServiceResponse createService(HospitalServiceRequest request) {
        Long tenantId = TenantContext.getTenantId();

        // Check for duplicate service code
        hospitalServiceRepository.findByServiceCodeAndTenantId(request.getServiceCode(), tenantId)
                .ifPresent(s -> {
                    throw new DuplicateResourceException("HospitalService", "serviceCode", request.getServiceCode());
                });

        HospitalService service = HospitalService.builder()
                .serviceName(request.getServiceName())
                .serviceCode(request.getServiceCode())
                .price(request.getPrice())
                .itemType(request.getItemType())
                .active(request.isActive())
                .description(request.getDescription())
                .validityPeriodDays(request.getValidityPeriodDays())
                .isInsurancePayable(request.getIsInsurancePayable() != null ? request.getIsInsurancePayable() : true)
                .insuranceRate(request.getInsuranceRate())
                .gstPercentage(request.getGstPercentage())
                .effectiveFrom(request.getEffectiveFrom())
                .build();
        
        service.setTenantId(tenantId); // Inherited from TenantAwareEntity

        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));
            service.setDepartment(dept);
        }

        service = hospitalServiceRepository.save(service);
        log.info("Created hospital service: {} for tenant: {}", service.getServiceCode(), tenantId);
        return mapToResponse(service);
    }

    @Transactional
    public HospitalServiceResponse updateService(Long id, HospitalServiceRequest request) {
        Long tenantId = TenantContext.getTenantId();

        HospitalService service = hospitalServiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("HospitalService", "id", id));

        // Check for duplicate service code if changed
        if (!service.getServiceCode().equals(request.getServiceCode())) {
            hospitalServiceRepository.findByServiceCodeAndTenantId(request.getServiceCode(), tenantId)
                    .ifPresent(s -> {
                        throw new DuplicateResourceException("HospitalService", "serviceCode", request.getServiceCode());
                    });
        }

        service.setServiceName(request.getServiceName());
        service.setServiceCode(request.getServiceCode());
        service.setPrice(request.getPrice());
        service.setItemType(request.getItemType());
        service.setActive(request.isActive());
        service.setDescription(request.getDescription());
        service.setValidityPeriodDays(request.getValidityPeriodDays());
        service.setIsInsurancePayable(request.getIsInsurancePayable() != null ? request.getIsInsurancePayable() : true);
        service.setInsuranceRate(request.getInsuranceRate());
        service.setGstPercentage(request.getGstPercentage());
        service.setEffectiveFrom(request.getEffectiveFrom());

        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));
            service.setDepartment(dept);
        } else {
            service.setDepartment(null);
        }

        service = hospitalServiceRepository.save(service);
        log.info("Updated hospital service: {} for tenant: {}", service.getServiceCode(), tenantId);
        return mapToResponse(service);
    }

    @Transactional
    public void deleteService(Long id) {
        Long tenantId = TenantContext.getTenantId();
        HospitalService service = hospitalServiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("HospitalService", "id", id));
        hospitalServiceRepository.delete(service);
        log.info("Permanently deleted hospital service: {} for tenant: {}", id, tenantId);
    }

    private HospitalServiceResponse mapToResponse(HospitalService service) {
        return HospitalServiceResponse.builder()
                .id(service.getId())
                .serviceName(service.getServiceName())
                .serviceCode(service.getServiceCode())
                .price(service.getPrice())
                .itemType(service.getItemType())
                .active(service.isActive())
                .description(service.getDescription())
                .validityPeriodDays(service.getValidityPeriodDays())
                .isInsurancePayable(service.getIsInsurancePayable())
                .departmentId(service.getDepartment() != null ? service.getDepartment().getId() : null)
                .departmentName(service.getDepartment() != null ? service.getDepartment().getName() : null)
                .insuranceRate(service.getInsuranceRate())
                .gstPercentage(service.getGstPercentage())
                .build();
    }
}
