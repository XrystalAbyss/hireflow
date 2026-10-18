package com.hireflow.api.controller.officer;

import com.hireflow.api.dto.PagedResponse;
import com.hireflow.api.dto.officer.OfficerDriveCreateDto;
import com.hireflow.api.dto.officer.OfficerDriveDetailDto;
import com.hireflow.api.dto.officer.OfficerDriveUpdateDto;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.ApplicationRepository;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.service.RecruitmentDriveService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller providing placement officer recruitment drive management:
 * <ul>
 *     <li>GET  /api/v1/officer/drives              - Bounded paginated list of drives</li>
 *     <li>GET  /api/v1/officer/drives/{id}         - Detailed drive specifications</li>
 *     <li>POST /api/v1/officer/drives              - Create recruitment drive</li>
 *     <li>PUT  /api/v1/officer/drives/{id}         - Update recruitment drive</li>
 *     <li>POST /api/v1/officer/drives/{id}/close   - Authoritatively close recruitment drive</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/officer/drives")
public class OfficerRecruitmentDriveController {

    private static final Logger log = LoggerFactory.getLogger(OfficerRecruitmentDriveController.class);

    private static final HttpHeaders ANTI_CACHE_HEADERS = new HttpHeaders();
    static {
        ANTI_CACHE_HEADERS.setCacheControl("no-cache, no-store, must-revalidate");
        ANTI_CACHE_HEADERS.setPragma("no-cache");
        ANTI_CACHE_HEADERS.setExpires(0);
    }

    private final RecruitmentDriveService driveService;
    private final DriveRepository driveRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final TransactionManager transactionManager;

    public OfficerRecruitmentDriveController(
            RecruitmentDriveService driveService,
            DriveRepository driveRepository,
            CompanyRepository companyRepository,
            ApplicationRepository applicationRepository,
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        this.driveService = Objects.requireNonNull(driveService, "RecruitmentDriveService cannot be null.");
        this.driveRepository = Objects.requireNonNull(driveRepository, "DriveRepository cannot be null.");
        this.companyRepository = Objects.requireNonNull(companyRepository, "CompanyRepository cannot be null.");
        this.applicationRepository = Objects.requireNonNull(applicationRepository, "ApplicationRepository cannot be null.");
        this.transactionManager = Objects.requireNonNull(transactionManager, "TransactionManager cannot be null.");
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OfficerDriveDetailDto>> getDrives(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "companyId", required = false) String companyId,
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {

        int boundedPage = Math.max(0, page);
        int boundedSize = Math.clamp(size, 1, 100);

        List<RecruitmentDrive> drives = (companyId != null && !companyId.isBlank())
                ? driveRepository.findByCompanyId(companyId.trim().toUpperCase())
                : driveRepository.findAll();

        LocalDate now = LocalDate.now();
        if (activeOnly) {
            drives = drives.stream()
                    .filter(d -> !d.isClosed() && !d.isDeadlinePassed(now))
                    .toList();
        }

        Map<String, Company> companyMap = companyRepository.findAll().stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        List<Application> allApps = applicationRepository.findAll();
        Map<String, List<Application>> appsByDrive = allApps.stream()
                .collect(Collectors.groupingBy(a -> a.getDriveId().toUpperCase()));

        List<OfficerDriveDetailDto> dtoList = new ArrayList<>();
        for (RecruitmentDrive drive : drives) {
            String cName = Optional.ofNullable(companyMap.get(drive.getCompanyId().toUpperCase()))
                    .map(Company::getName)
                    .orElse(drive.getCompanyId());

            List<Application> driveApps = appsByDrive.getOrDefault(drive.getId().toUpperCase(), List.of());
            int totalApps = driveApps.size();
            int shortlisted = (int) driveApps.stream()
                    .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED)
                    .count();

            dtoList.add(OfficerDriveDetailDto.fromDomain(drive, cName, totalApps, shortlisted));
        }

        dtoList.sort(Comparator.comparing(OfficerDriveDetailDto::id));

        int totalElements = dtoList.size();
        int totalPages = (int) Math.ceil((double) totalElements / boundedSize);
        int fromIndex = Math.min(boundedPage * boundedSize, totalElements);
        int toIndex = Math.min(fromIndex + boundedSize, totalElements);

        List<OfficerDriveDetailDto> pagedContent = (fromIndex < totalElements)
                ? dtoList.subList(fromIndex, toIndex)
                : Collections.emptyList();

        boolean last = (boundedPage >= totalPages - 1);
        PagedResponse<OfficerDriveDetailDto> response = new PagedResponse<>(
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
    public ResponseEntity<OfficerDriveDetailDto> getDriveById(@PathVariable("id") String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Drive ID cannot be null or empty.");
        }
        String normId = id.trim().toUpperCase();
        RecruitmentDrive drive = driveService.getDriveOrThrow(normId);

        String companyName = companyRepository.findById(drive.getCompanyId())
                .map(Company::getName)
                .orElse(drive.getCompanyId());

        List<Application> driveApps = applicationRepository.findByDriveId(normId);
        int totalApps = driveApps.size();
        int shortlisted = (int) driveApps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED)
                .count();

        OfficerDriveDetailDto dto = OfficerDriveDetailDto.fromDomain(drive, companyName, totalApps, shortlisted);
        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    @PostMapping
    public ResponseEntity<OfficerDriveDetailDto> createDrive(
            @Valid @RequestBody OfficerDriveCreateDto createDto) {

        RecruitmentDrive created = transactionManager.runInTransaction(conn ->
                driveService.createDrive(
                        createDto.id(),
                        createDto.companyId(),
                        createDto.jobTitle(),
                        createDto.minimumCgpa(),
                        createDto.eligibleBranches(),
                        createDto.applicationDeadline()
                )
        );

        log.info("[OfficerPortal] Recruitment drive '{}' for company '{}' created successfully.",
                created.getId(), created.getCompanyId());

        String companyName = companyRepository.findById(created.getCompanyId())
                .map(Company::getName)
                .orElse(created.getCompanyId());

        OfficerDriveDetailDto dto = OfficerDriveDetailDto.fromDomain(created, companyName, 0, 0);
        URI location = URI.create("/api/v1/officer/drives/" + created.getId());

        return ResponseEntity.created(location)
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OfficerDriveDetailDto> updateDrive(
            @PathVariable("id") String id,
            @Valid @RequestBody OfficerDriveUpdateDto updateDto) {

        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Drive ID cannot be null or empty.");
        }
        String normId = id.trim().toUpperCase();

        RecruitmentDrive updated = transactionManager.runInTransaction(conn -> {
            RecruitmentDrive existing = driveService.getDriveOrThrow(normId);
            if (existing.isClosed()) {
                throw new ValidationException("Cannot update recruitment drive '" + normId + "': drive is closed.");
            }
            existing.setJobTitle(updateDto.jobTitle());
            existing.setMinimumCgpa(updateDto.minimumCgpa());
            existing.setEligibleBranches(updateDto.eligibleBranches());
            existing.setApplicationDeadline(updateDto.applicationDeadline());
            return driveService.updateDrive(existing);
        });

        log.info("[OfficerPortal] Recruitment drive '{}' updated successfully.", updated.getId());

        String companyName = companyRepository.findById(updated.getCompanyId())
                .map(Company::getName)
                .orElse(updated.getCompanyId());

        List<Application> driveApps = applicationRepository.findByDriveId(normId);
        int totalApps = driveApps.size();
        int shortlisted = (int) driveApps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED)
                .count();

        OfficerDriveDetailDto dto = OfficerDriveDetailDto.fromDomain(updated, companyName, totalApps, shortlisted);
        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<OfficerDriveDetailDto> closeDrive(@PathVariable("id") String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Drive ID cannot be null or empty.");
        }
        String normId = id.trim().toUpperCase();

        // Transactional closure with authoritative row lock serialization established in Stage 4C.1
        RecruitmentDrive closed = transactionManager.runInTransaction(conn ->
                driveService.closeDrive(normId)
        );

        log.info("[OfficerPortal] Recruitment drive '{}' closed successfully.", closed.getId());

        String companyName = companyRepository.findById(closed.getCompanyId())
                .map(Company::getName)
                .orElse(closed.getCompanyId());

        List<Application> driveApps = applicationRepository.findByDriveId(normId);
        int totalApps = driveApps.size();
        int shortlisted = (int) driveApps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED)
                .count();

        OfficerDriveDetailDto dto = OfficerDriveDetailDto.fromDomain(closed, companyName, totalApps, shortlisted);
        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }
}
