package com.thiru.wealthlens.taxplanning.salary.service;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.taxplanning.enums.ProfileType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import com.thiru.wealthlens.taxplanning.policy.service.PolicyService;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryComponentDto;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileRequest;
import com.thiru.wealthlens.taxplanning.salary.dto.SalaryProfileResponse;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryComponentEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.SalaryProfileEntity;
import com.thiru.wealthlens.taxplanning.salary.entity.TaxComputationEntity;
import com.thiru.wealthlens.taxplanning.salary.repository.SalaryProfileRepository;
import com.thiru.wealthlens.taxplanning.salary.repository.TaxComputationRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
@RequiredArgsConstructor
@Transactional
public class SalaryProfileService {

    private final SalaryProfileRepository profileRepo;
    private final TaxComputationRepository computationRepo;
    private final PolicyService policyService;

    public SalaryProfileResponse createProfile(String email, SalaryProfileRequest request) {
        validateRequest(request);

        // Validate that total CTC is consistent (sum of components)
        long componentSum = request.getComponents() == null ? 0L
                : request.getComponents().stream()
                        .mapToLong(c -> c.getAnnualAmount() != null ? c.getAnnualAmount() : 0L)
                        .sum();
        if (componentSum <= 0) {
            throw new IllegalArgumentException("Total CTC must be greater than zero");
        }

        // Derive isMetroCity
        boolean isMetroCity = deriveIsMetroCity(request.getCityName());

        // Build entity from request
        SalaryProfileEntity entity = new SalaryProfileEntity();
        entity.setEmail(email);
        entity.setProfileName(request.getProfileName());
        entity.setProfileType(request.getProfileType() != null ? request.getProfileType() : ProfileType.CURRENT);
        entity.setEmployerName(request.getEmployerName());
        entity.setTaxYear(request.getTaxYear());
        entity.setCityTier(request.getCityTier());
        entity.setCityName(request.getCityName());
        entity.setRegimeType(request.getRegimeType() != null ? request.getRegimeType() : RegimeType.NOT_DECIDED);
        entity.setComponents(toComponentEntities(request.getComponents()));
        entity.setEmployerType(request.getEmployerType());
        entity.setCarProvided(request.getCarProvided());
        entity.setCarOwnership(request.getCarOwnership());
        entity.setCarEngineSize(request.getCarEngineSize());
        entity.setDriverProvidedByEmployer(request.getDriverProvidedByEmployer());
        entity.setDriverSalaryMonthly(request.getDriverSalaryMonthly());
        entity.setInvestment80c(request.getInvestment80c());
        entity.setInvestment80d(request.getInvestment80d());
        entity.setHomeLoanInterest(request.getHomeLoanInterest());
        entity.setNpsSelf80ccd1b(request.getNpsSelf80ccd1b());
        entity.setMonthlyRentPaid(request.getMonthlyRentPaid() != null ? request.getMonthlyRentPaid() : 0L);
        entity.setIsPayingRent(request.getIsPayingRent());
        entity.setIsMetroCity(isMetroCity);
        entity.setNumberOfChildren(request.getNumberOfChildren());
        entity.setIsPhysicallyDisabled(request.getIsPhysicallyDisabled());
        entity.setLtaBlockStart(request.getLtaBlockStart());
        entity.setLtaTripsClaimedInBlock(request.getLtaTripsClaimedInBlock());
        entity.setLtaCarryForwardAvailable(request.getLtaCarryForwardAvailable());
        entity.setStatus(EntityStatus.ACTIVE);

        SalaryProfileEntity saved = profileRepo.save(entity);
        log.info("Created salary profile {} for email {}", saved.getId(), email);

        if (request.getRegimeType() == RegimeType.NOT_DECIDED) {
            log.warn("Profile {} created with NOT_DECIDED regime. Tax computations require a chosen regime.", saved.getId());
        }

        return SalaryProfileResponse.fromEntity(saved);
    }

    public SalaryProfileResponse updateProfile(String email, String profileId, SalaryProfileRequest request) {
        validateRequest(request);

        SalaryProfileEntity existing = profileRepo.findById(profileId)
                .filter(p -> p.getEmail().equals(email))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Salary profile not found with id=" + profileId + " for email=" + email));

        // If not paying rent, reset monthlyRentPaid
        if (Boolean.FALSE.equals(request.getIsPayingRent())) {
            request.setMonthlyRentPaid(0L);
        }

        // Recompute isMetroCity
        boolean isMetroCity = deriveIsMetroCity(request.getCityName());

        // Update fields
        existing.setProfileName(request.getProfileName());
        existing.setProfileType(request.getProfileType());
        existing.setEmployerName(request.getEmployerName());
        existing.setTaxYear(request.getTaxYear());
        existing.setCityTier(request.getCityTier());
        existing.setCityName(request.getCityName());
        existing.setRegimeType(request.getRegimeType());
        existing.setComponents(toComponentEntities(request.getComponents()));
        existing.setEmployerType(request.getEmployerType());
        existing.setCarProvided(request.getCarProvided());
        existing.setCarOwnership(request.getCarOwnership());
        existing.setCarEngineSize(request.getCarEngineSize());
        existing.setDriverProvidedByEmployer(request.getDriverProvidedByEmployer());
        existing.setDriverSalaryMonthly(request.getDriverSalaryMonthly());
        existing.setInvestment80c(request.getInvestment80c());
        existing.setInvestment80d(request.getInvestment80d());
        existing.setHomeLoanInterest(request.getHomeLoanInterest());
        existing.setNpsSelf80ccd1b(request.getNpsSelf80ccd1b());
        existing.setMonthlyRentPaid(request.getMonthlyRentPaid());
        existing.setIsPayingRent(request.getIsPayingRent());
        existing.setIsMetroCity(isMetroCity);
        existing.setNumberOfChildren(request.getNumberOfChildren());
        existing.setIsPhysicallyDisabled(request.getIsPhysicallyDisabled());
        existing.setLtaBlockStart(request.getLtaBlockStart());
        existing.setLtaTripsClaimedInBlock(request.getLtaTripsClaimedInBlock());
        existing.setLtaCarryForwardAvailable(request.getLtaCarryForwardAvailable());

        SalaryProfileEntity saved = profileRepo.save(existing);

        // Invalidate cached tax computations
        computationRepo.deleteAll(computationRepo.findBySalaryProfileId(profileId));
        log.info("Updated salary profile {} and invalidated cached computations", profileId);

        return SalaryProfileResponse.fromEntity(saved);
    }

    public void deleteProfile(String email, String profileId) {
        SalaryProfileEntity existing = profileRepo.findById(profileId)
                .filter(p -> p.getEmail().equals(email))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Salary profile not found with id=" + profileId + " for email=" + email));

        // Delete associated tax computations
        List<TaxComputationEntity> computations = computationRepo.findBySalaryProfileId(profileId);
        computationRepo.deleteAll(computations);

        profileRepo.delete(existing);
        log.info("Deleted salary profile {} for email {}", profileId, email);
    }

    public SalaryProfileResponse getProfile(String email, String profileId) {
        SalaryProfileEntity entity = profileRepo.findById(profileId)
                .filter(p -> p.getEmail().equals(email))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Salary profile not found with id=" + profileId + " for email=" + email));
        return SalaryProfileResponse.fromEntity(entity);
    }

    public SalaryProfileEntity getProfileEntity(String email, String profileId) {
        return profileRepo.findById(profileId)
                .filter(p -> p.getEmail().equals(email))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Salary profile not found with id=" + profileId + " for email=" + email));
    }

    public List<SalaryProfileResponse> getProfiles(String email) {
        return profileRepo.findByEmailOrderByAuditMetadata_CreatedAtDesc(email).stream()
                .map(SalaryProfileResponse::fromEntity)
                .collect(Collectors.toList());
    }

    boolean deriveIsMetroCity(String cityName) {
        return SalaryProfileResponse.deriveIsMetroCity(cityName);
    }

    private void validateRequest(SalaryProfileRequest request) {
        if (request.getMonthlyRentPaid() != null && request.getMonthlyRentPaid() < 0) {
            throw new IllegalArgumentException("monthlyRentPaid must be >= 0");
        }
        if (request.getNumberOfChildren() != null &&
                (request.getNumberOfChildren() < 0 || request.getNumberOfChildren() > 10)) {
            throw new IllegalArgumentException("numberOfChildren must be between 0 and 10");
        }
        if (request.getInvestment80c() != null && request.getInvestment80c() < 0) {
            throw new IllegalArgumentException("investment80c must be >= 0");
        }
        if (request.getInvestment80d() != null && request.getInvestment80d() < 0) {
            throw new IllegalArgumentException("investment80d must be >= 0");
        }
        if (request.getHomeLoanInterest() != null && request.getHomeLoanInterest() < 0) {
            throw new IllegalArgumentException("homeLoanInterest must be >= 0");
        }
        if (request.getNpsSelf80ccd1b() != null && request.getNpsSelf80ccd1b() < 0) {
            throw new IllegalArgumentException("npsSelf80ccd1b must be >= 0");
        }
        if (request.getComponents() != null) {
            for (SalaryComponentDto component : request.getComponents()) {
                if (component.getAnnualAmount() != null && component.getAnnualAmount() < 0) {
                    throw new IllegalArgumentException(
                            "annualAmount for component " + component.getAllowanceCode() + " must be >= 0");
                }
            }
        }
    }

    private List<SalaryComponentEntity> toComponentEntities(List<SalaryComponentDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(dto -> {
                    SalaryComponentEntity entity = new SalaryComponentEntity();
                    entity.setAllowanceCode(dto.getAllowanceCode());
                    entity.setAnnualAmount(dto.getAnnualAmount());
                    entity.setIsCurrent(dto.getIsCurrent());
                    entity.setNotes(dto.getNotes());
                    return entity;
                })
                .collect(Collectors.toList());
    }
}
