package com.hireflow.api.controller.officer;

import com.hireflow.api.dto.PagedResponse;
import com.hireflow.api.dto.officer.OfficerApplicationReviewDto;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.StudentRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.RecruitmentDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller providing placement officer applicant review and shortlisting management:
 * <ul>
 *     <li>GET  /api/v1/officer/drives/{id}/applications - Bounded paginated candidate roster for a drive</li>
 *     <li>GET  /api/v1/officer/applications/{id}        - Detailed application candidate review</li>
 *     <li>POST /api/v1/officer/applications/{id}/shortlist - Authoritative status transition to SHORTLISTED</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/officer")
public class OfficerApplicationController {

    private static final Logger log = LoggerFactory.getLogger(OfficerApplicationController.class);

    private static final HttpHeaders ANTI_CACHE_HEADERS = new HttpHeaders();
    static {
        ANTI_CACHE_HEADERS.setCacheControl("no-cache, no-store, must-revalidate");
        ANTI_CACHE_HEADERS.setPragma("no-cache");
        ANTI_CACHE_HEADERS.setExpires(0);
    }

    private final ApplicationService applicationService;
    private final RecruitmentDriveService driveService;
    private final DriveRepository driveRepository;
    private final StudentRepository studentRepository;
    private final CompanyRepository companyRepository;
    private final TransactionManager transactionManager;

    public OfficerApplicationController(
            ApplicationService applicationService,
            RecruitmentDriveService driveService,
            DriveRepository driveRepository,
            StudentRepository studentRepository,
            CompanyRepository companyRepository,
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        this.applicationService = Objects.requireNonNull(applicationService, "ApplicationService cannot be null.");
        this.driveService = Objects.requireNonNull(driveService, "RecruitmentDriveService cannot be null.");
        this.driveRepository = Objects.requireNonNull(driveRepository, "DriveRepository cannot be null.");
        this.studentRepository = Objects.requireNonNull(studentRepository, "StudentRepository cannot be null.");
        this.companyRepository = Objects.requireNonNull(companyRepository, "CompanyRepository cannot be null.");
        this.transactionManager = Objects.requireNonNull(transactionManager, "TransactionManager cannot be null.");
    }

    @GetMapping("/drives/{id}/applications")
    public ResponseEntity<PagedResponse<OfficerApplicationReviewDto>> getApplicationsForDrive(
            @PathVariable("id") String driveId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) String statusFilter) {

        if (driveId == null || driveId.trim().isEmpty()) {
            throw new ValidationException("Drive ID cannot be null or empty.");
        }
        String normDriveId = driveId.trim().toUpperCase();

        // Verify drive exists
        RecruitmentDrive drive = driveService.getDriveOrThrow(normDriveId);
        String jobTitle = drive.getJobTitle();
        String companyName = companyRepository.findById(drive.getCompanyId())
                .map(Company::getName)
                .orElse(drive.getCompanyId());

        int boundedPage = Math.max(0, page);
        int boundedSize = Math.clamp(size, 1, 100);

        List<Application> apps = applicationService.getApplicationsForDrive(normDriveId);

        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                ApplicationStatus requiredStatus = ApplicationStatus.valueOf(statusFilter.trim().toUpperCase());
                apps = apps.stream()
                        .filter(a -> a.getStatus() == requiredStatus)
                        .toList();
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Invalid status filter: '" + statusFilter + "'.");
            }
        }

        List<OfficerApplicationReviewDto> dtoList = new ArrayList<>();
        for (Application app : apps) {
            Student student = studentRepository.findById(app.getStudentId()).orElse(null);
            dtoList.add(OfficerApplicationReviewDto.fromDomain(app, student, jobTitle, companyName));
        }

        // Sort deterministically: appliedAt desc, id asc
        dtoList.sort(Comparator.comparing(OfficerApplicationReviewDto::appliedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(OfficerApplicationReviewDto::id));

        int totalElements = dtoList.size();
        int totalPages = (int) Math.ceil((double) totalElements / boundedSize);
        int fromIndex = Math.min(boundedPage * boundedSize, totalElements);
        int toIndex = Math.min(fromIndex + boundedSize, totalElements);

        List<OfficerApplicationReviewDto> pagedContent = (fromIndex < totalElements)
                ? dtoList.subList(fromIndex, toIndex)
                : Collections.emptyList();

        boolean last = (boundedPage >= totalPages - 1);
        PagedResponse<OfficerApplicationReviewDto> response = new PagedResponse<>(
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

    @GetMapping("/applications/{id}")
    public ResponseEntity<OfficerApplicationReviewDto> getApplicationById(@PathVariable("id") String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Application ID cannot be null or empty.");
        }
        String normId = id.trim().toUpperCase();
        Application app = applicationService.getApplicationOrThrow(normId);

        Student student = studentRepository.findById(app.getStudentId()).orElse(null);
        RecruitmentDrive drive = driveRepository.findById(app.getDriveId()).orElse(null);
        String jobTitle = drive != null ? drive.getJobTitle() : app.getDriveId();
        String companyName = (drive != null)
                ? companyRepository.findById(drive.getCompanyId()).map(Company::getName).orElse(drive.getCompanyId())
                : "Corporate Partner";

        OfficerApplicationReviewDto dto = OfficerApplicationReviewDto.fromDomain(app, student, jobTitle, companyName);
        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    @PostMapping("/applications/{id}/shortlist")
    public ResponseEntity<OfficerApplicationReviewDto> shortlistApplication(@PathVariable("id") String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Application ID cannot be null or empty.");
        }
        String normId = id.trim().toUpperCase();

        // Transactional shortlisting with atomic conditional status update
        Application updated = transactionManager.runInTransaction(conn ->
                applicationService.shortlistApplication(normId, false)
        );

        log.info("[OfficerPortal] Application '{}' shortlisted successfully.", updated.getId());

        Student student = studentRepository.findById(updated.getStudentId()).orElse(null);
        RecruitmentDrive drive = driveRepository.findById(updated.getDriveId()).orElse(null);
        String jobTitle = drive != null ? drive.getJobTitle() : updated.getDriveId();
        String companyName = (drive != null)
                ? companyRepository.findById(drive.getCompanyId()).map(Company::getName).orElse(drive.getCompanyId())
                : "Corporate Partner";

        OfficerApplicationReviewDto dto = OfficerApplicationReviewDto.fromDomain(updated, student, jobTitle, companyName);
        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }
}
