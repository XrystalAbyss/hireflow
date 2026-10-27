package com.hireflow.api.controller.officer;

import com.hireflow.api.dto.PagedResponse;
import com.hireflow.api.dto.officer.OfficerCompanyCreateDto;
import com.hireflow.api.dto.officer.OfficerCompanyDetailDto;
import com.hireflow.api.dto.officer.OfficerCompanyUpdateDto;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.service.CompanyService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller providing placement officer company and corporate partner management:
 * <ul>
 *     <li>GET  /api/v1/officer/companies          - Bounded paginated list of companies</li>
 *     <li>GET  /api/v1/officer/companies/{id}     - Detailed company profile</li>
 *     <li>POST /api/v1/officer/companies          - Register new corporate partner</li>
 *     <li>PUT  /api/v1/officer/companies/{id}     - Update corporate partner</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/officer/companies")
public class OfficerCompanyController {

    private static final Logger log = LoggerFactory.getLogger(OfficerCompanyController.class);

    private static final HttpHeaders ANTI_CACHE_HEADERS = new HttpHeaders();
    static {
        ANTI_CACHE_HEADERS.setCacheControl("no-cache, no-store, must-revalidate");
        ANTI_CACHE_HEADERS.setPragma("no-cache");
        ANTI_CACHE_HEADERS.setExpires(0);
    }

    private final CompanyService companyService;
    private final CompanyRepository companyRepository;
    private final DriveRepository driveRepository;
    private final TransactionManager transactionManager;

    public OfficerCompanyController(
            CompanyService companyService,
            CompanyRepository companyRepository,
            DriveRepository driveRepository,
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        this.companyService = Objects.requireNonNull(companyService, "CompanyService cannot be null.");
        this.companyRepository = Objects.requireNonNull(companyRepository, "CompanyRepository cannot be null.");
        this.driveRepository = Objects.requireNonNull(driveRepository, "DriveRepository cannot be null.");
        this.transactionManager = Objects.requireNonNull(transactionManager, "TransactionManager cannot be null.");
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OfficerCompanyDetailDto>> getCompanies(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "search", required = false) String search) {

        int boundedPage = Math.max(0, page);
        int boundedSize = Math.clamp(size, 1, 100);

        List<Company> allCompanies = companyRepository.findAll();
        List<RecruitmentDrive> allDrives = driveRepository.findAll();
        LocalDate now = LocalDate.now();

        // Map drives by company ID
        Map<String, List<RecruitmentDrive>> drivesByCompany = allDrives.stream()
                .collect(Collectors.groupingBy(d -> d.getCompanyId().toUpperCase()));

        List<OfficerCompanyDetailDto> items = new ArrayList<>();
        for (Company c : allCompanies) {
            if (search != null && !search.isBlank()) {
                String q = search.trim().toLowerCase();
                boolean matchesName = c.getName() != null && c.getName().toLowerCase().contains(q);
                boolean matchesInd = c.getIndustry() != null && c.getIndustry().toLowerCase().contains(q);
                boolean matchesId = c.getId() != null && c.getId().toLowerCase().contains(q);
                if (!matchesName && !matchesInd && !matchesId) {
                    continue;
                }
            }

            List<RecruitmentDrive> cDrives = drivesByCompany.getOrDefault(c.getId().toUpperCase(), List.of());
            int totalDrives = cDrives.size();
            int activeDrives = (int) cDrives.stream()
                    .filter(d -> !d.isClosed() && !d.isDeadlinePassed(now))
                    .count();

            items.add(OfficerCompanyDetailDto.fromDomain(c, activeDrives, totalDrives));
        }

        items.sort(Comparator.comparing(OfficerCompanyDetailDto::id));

        int totalElements = items.size();
        int totalPages = (int) Math.ceil((double) totalElements / boundedSize);
        int fromIndex = Math.min(boundedPage * boundedSize, totalElements);
        int toIndex = Math.min(fromIndex + boundedSize, totalElements);

        List<OfficerCompanyDetailDto> pagedContent = (fromIndex < totalElements)
                ? items.subList(fromIndex, toIndex)
                : Collections.emptyList();

        boolean last = (boundedPage >= totalPages - 1);
        PagedResponse<OfficerCompanyDetailDto> response = new PagedResponse<>(
                pagedContent,
                boundedPage,
                boundedSize,
                totalElements,
                totalPages,
                last
        );

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfficerCompanyDetailDto> getCompanyById(@PathVariable("id") String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Company ID cannot be null or empty.");
        }
        String normId = id.trim().toUpperCase();
        Company company = companyService.getCompanyOrThrow(normId);

        List<RecruitmentDrive> drives = driveRepository.findByCompanyId(normId);
        LocalDate now = LocalDate.now();
        int activeDrives = (int) drives.stream()
                .filter(d -> !d.isClosed() && !d.isDeadlinePassed(now))
                .count();

        OfficerCompanyDetailDto dto = OfficerCompanyDetailDto.fromDomain(company, activeDrives, drives.size());
        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    @PostMapping
    public ResponseEntity<OfficerCompanyDetailDto> createCompany(
            @Valid @RequestBody OfficerCompanyCreateDto createDto) {

        Company created = transactionManager.runInTransaction(conn ->
                companyService.registerCompany(createDto.id(), createDto.name(), createDto.industry())
        );

        log.info("[OfficerPortal] Company '{}' ('{}') registered successfully.", created.getId(), created.getName());
        OfficerCompanyDetailDto dto = OfficerCompanyDetailDto.fromDomain(created, 0, 0);
        URI location = URI.create("/api/v1/officer/companies/" + created.getId());

        return ResponseEntity.created(location)
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OfficerCompanyDetailDto> updateCompany(
            @PathVariable("id") String id,
            @Valid @RequestBody OfficerCompanyUpdateDto updateDto) {

        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Company ID cannot be null or empty.");
        }
        String normId = id.trim().toUpperCase();

        Company updated = transactionManager.runInTransaction(conn ->
                companyService.updateCompany(normId, updateDto.name(), updateDto.industry())
        );

        log.info("[OfficerPortal] Company '{}' updated successfully.", updated.getId());

        List<RecruitmentDrive> drives = driveRepository.findByCompanyId(normId);
        LocalDate now = LocalDate.now();
        int activeDrives = (int) drives.stream()
                .filter(d -> !d.isClosed() && !d.isDeadlinePassed(now))
                .count();

        OfficerCompanyDetailDto dto = OfficerCompanyDetailDto.fromDomain(updated, activeDrives, drives.size());
        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }
}
