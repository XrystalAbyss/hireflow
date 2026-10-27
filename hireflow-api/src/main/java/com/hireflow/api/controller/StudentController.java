package com.hireflow.api.controller;

import com.hireflow.api.dto.ApplicationSubmitRequestDto;
import com.hireflow.api.dto.PagedResponse;
import com.hireflow.api.dto.StudentApplicationDetailDto;
import com.hireflow.api.dto.StudentDashboardDto;
import com.hireflow.api.dto.StudentDriveEligibilityDto;
import com.hireflow.api.dto.StudentDriveItemDto;
import com.hireflow.api.dto.StudentProfileDto;
import com.hireflow.api.security.HireFlowUserPrincipal;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.api.dto.student.StudentInterviewDto;
import com.hireflow.model.InterviewSession;
import com.hireflow.model.Interviewer;
import com.hireflow.model.Room;
import com.hireflow.repository.InterviewSessionRepository;
import com.hireflow.repository.InterviewerRepository;
import com.hireflow.repository.RoomRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.service.StudentService;
import com.hireflow.web.security.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REST controller managing authenticated candidate workflows:
 * - Candidate profile inspection (GET /profile)
 * - Tailored drives directory with real-time academic eligibility (GET /drives)
 * - Drive-specific eligibility check (GET /drives/{id}/eligibility)
 * - Student application tracking with bounded pagination (GET /applications)
 * - Student application inspection with strict ownership enforcement (GET /applications/{id})
 * - Transactional application submission (POST /applications)
 * - Candidate placement activity dashboard (GET /dashboard)
 * <p>
 * Key Security & Architecture Invariants:
 * 1. Authoritative student identity is derived exclusively from the authenticated {@link HireFlowUserPrincipal}.
 * 2. Client-supplied student IDs in headers, query params, or body are strictly untrusted and ignored.
 * 3. Cross-student application inspection is defended with non-disclosing HTTP 404 responses.
 * 4. Submissions run inside a dedicated HireFlow JDBC {@link TransactionManager} with zero mixed JPA write contexts.
 * 5. All responses enforce strict anti-caching headers (Cache-Control: no-store).
 */
@RestController
@RequestMapping("/api/v1/student")
public class StudentController {

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);

    private static final HttpHeaders ANTI_CACHE_HEADERS;
    static {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0);
        ANTI_CACHE_HEADERS = HttpHeaders.readOnlyHttpHeaders(headers);
    }

    private final StudentService studentService;
    private final RecruitmentDriveService driveService;
    private final ApplicationService applicationService;
    private final EligibilityService eligibilityService;
    private final CompanyRepository companyRepository;
    private final TransactionManager transactionManager;
    private final InterviewSessionRepository sessionRepository;
    private final RoomRepository roomRepository;
    private final InterviewerRepository interviewerRepository;

    public StudentController(
            StudentService studentService,
            RecruitmentDriveService driveService,
            ApplicationService applicationService,
            EligibilityService eligibilityService,
            CompanyRepository companyRepository,
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        this(studentService, driveService, applicationService, eligibilityService, companyRepository, transactionManager, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StudentController(
            StudentService studentService,
            RecruitmentDriveService driveService,
            ApplicationService applicationService,
            EligibilityService eligibilityService,
            CompanyRepository companyRepository,
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager,
            InterviewSessionRepository sessionRepository,
            RoomRepository roomRepository,
            InterviewerRepository interviewerRepository) {
        this.studentService = studentService;
        this.driveService = driveService;
        this.applicationService = applicationService;
        this.eligibilityService = eligibilityService;
        this.companyRepository = companyRepository;
        this.transactionManager = transactionManager;
        this.sessionRepository = sessionRepository;
        this.roomRepository = roomRepository;
        this.interviewerRepository = interviewerRepository;
    }

    /**
     * Resolves and verifies the authenticated STUDENT principal.
     */
    private HireFlowUserPrincipal resolveStudentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof HireFlowUserPrincipal principal)) {
            throw new AccessDeniedException("Full authentication with STUDENT role is required.");
        }
        if (principal.getRole() != UserRole.STUDENT || principal.getStudentId() == null || principal.getStudentId().isBlank()) {
            throw new AccessDeniedException("Access Denied: Candidate operations require an authenticated STUDENT account with a linked studentId.");
        }
        return principal;
    }

    /**
     * GET /api/v1/student/profile
     * Returns the authenticated student's academic profile.
     */
    @GetMapping("/profile")
    public ResponseEntity<StudentProfileDto> getProfile() {
        HireFlowUserPrincipal principal = resolveStudentPrincipal();
        String studentId = principal.getStudentId();

        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student academic profile record could not be found for ID: " + studentId));

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(StudentProfileDto.fromDomain(student));
    }

    /**
     * GET /api/v1/student/drives
     * Returns candidate-tailored recruitment drives with real-time server-side eligibility evaluation.
     */
    @GetMapping("/drives")
    public ResponseEntity<List<StudentDriveItemDto>> getEligibleDrives() {
        HireFlowUserPrincipal principal = resolveStudentPrincipal();
        String studentId = principal.getStudentId();

        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student record could not be found for ID: " + studentId));

        List<RecruitmentDrive> allDrives = driveService.getAllDrives();
        List<Application> studentApps = applicationService.getApplicationsForStudent(studentId);
        Map<String, Application> appsByDriveId = studentApps.stream()
                .collect(Collectors.toMap(Application::getDriveId, Function.identity(), (a1, a2) -> a1));

        Map<String, Company> companyMap = companyRepository.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Function.identity(), (c1, c2) -> c1));

        LocalDate now = LocalDate.now();
        List<StudentDriveItemDto> items = new ArrayList<>();

        for (RecruitmentDrive drive : allDrives) {
            String companyName = Optional.ofNullable(companyMap.get(drive.getCompanyId()))
                    .map(Company::getName)
                    .orElse("Corporate Partner");

            Application existingApp = appsByDriveId.get(drive.getId());
            boolean hasApplied = (existingApp != null);
            String existingAppId = hasApplied ? existingApp.getId() : null;
            String existingAppStatus = hasApplied ? existingApp.getStatus().name() : null;

            EligibilityService.EligibilityResult academic = eligibilityService.checkAcademicEligibility(student, drive);
            EligibilityService.EligibilityResult full = eligibilityService.checkEligibility(student, drive, now);

            boolean deadlinePassed = drive.isDeadlinePassed(now);
            boolean acceptingApplications = !drive.isClosed() && !deadlinePassed;
            boolean canApply = !hasApplied && acceptingApplications && full.isEligible();

            items.add(new StudentDriveItemDto(
                    drive.getId(),
                    drive.getCompanyId(),
                    companyName,
                    drive.getJobTitle(),
                    drive.getMinimumCgpa(),
                    drive.getEligibleBranches(),
                    drive.getApplicationDeadline(),
                    drive.isClosed(),
                    deadlinePassed,
                    hasApplied,
                    existingAppId,
                    existingAppStatus,
                    academic.isEligible(),
                    acceptingApplications,
                    canApply,
                    full.reasons()
            ));
        }

        // Sort items: open & canApply first, then already applied, then deadline
        items.sort(Comparator
                .comparing((StudentDriveItemDto it) -> !it.canApply())
                .thenComparing((StudentDriveItemDto it) -> it.hasApplied())
                .thenComparing((StudentDriveItemDto it) -> it.closed() || it.deadlinePassed())
                .thenComparing(StudentDriveItemDto::applicationDeadline));

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(items);
    }

    /**
     * GET /api/v1/student/drives/{id}/eligibility
     * Returns detailed eligibility evaluation for a specific recruitment drive.
     */
    @GetMapping("/drives/{id}/eligibility")
    public ResponseEntity<StudentDriveEligibilityDto> getDriveEligibility(@PathVariable("id") String driveId) {
        if (driveId == null || driveId.trim().isEmpty()) {
            throw new ValidationException("Drive ID cannot be null or empty.");
        }
        HireFlowUserPrincipal principal = resolveStudentPrincipal();
        String studentId = principal.getStudentId();
        String normDriveId = driveId.trim().toUpperCase();

        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student record could not be found for ID: " + studentId));

        RecruitmentDrive drive = driveService.getDriveById(normDriveId)
                .orElseThrow(() -> new EntityNotFoundException("Recruitment drive with ID '" + normDriveId + "' was not found."));

        Optional<Application> existingApp = applicationService.getApplicationsForStudent(studentId).stream()
                .filter(a -> a.getDriveId().equalsIgnoreCase(normDriveId))
                .findFirst();

        LocalDate now = LocalDate.now();
        boolean hasApplied = existingApp.isPresent();
        String existingAppId = existingApp.map(Application::getId).orElse(null);
        String existingAppStatus = existingApp.map(a -> a.getStatus().name()).orElse(null);

        EligibilityService.EligibilityResult academic = eligibilityService.checkAcademicEligibility(student, drive);
        EligibilityService.EligibilityResult full = eligibilityService.checkEligibility(student, drive, now);

        boolean deadlinePassed = drive.isDeadlinePassed(now);
        boolean acceptingApplications = !drive.isClosed() && !deadlinePassed;
        boolean canApply = !hasApplied && acceptingApplications && full.isEligible();

        StudentDriveEligibilityDto dto = new StudentDriveEligibilityDto(
                drive.getId(),
                student.getId(),
                academic.isEligible(),
                acceptingApplications,
                hasApplied,
                canApply,
                existingAppId,
                existingAppStatus,
                drive.getMinimumCgpa(),
                student.getCgpa(),
                drive.getEligibleBranches(),
                student.getBranch(),
                drive.getApplicationDeadline(),
                drive.isClosed(),
                deadlinePassed,
                full.reasons()
        );

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    /**
     * GET /api/v1/student/applications
     * Returns the authenticated student's applications with deterministic sorting and bounded pagination.
     */
    @GetMapping("/applications")
    public ResponseEntity<PagedResponse<StudentApplicationDetailDto>> getApplications(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "appliedAt,desc") String sort) {

        HireFlowUserPrincipal principal = resolveStudentPrincipal();
        String studentId = principal.getStudentId();

        // Enforce pagination bounds
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));

        List<Application> applications = applicationService.getApplicationsForStudent(studentId);

        Map<String, RecruitmentDrive> driveMap = driveService.getAllDrives().stream()
                .collect(Collectors.toMap(RecruitmentDrive::getId, Function.identity(), (d1, d2) -> d1));
        Map<String, Company> companyMap = companyRepository.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Function.identity(), (c1, c2) -> c1));

        List<StudentApplicationDetailDto> dtoList = new ArrayList<>();
        for (Application app : applications) {
            RecruitmentDrive drive = driveMap.get(app.getDriveId());
            String jobTitle = drive != null ? drive.getJobTitle() : "Placement Drive";
            String companyId = drive != null ? drive.getCompanyId() : null;
            String companyName = Optional.ofNullable(companyId).map(companyMap::get).map(Company::getName).orElse("Corporate Partner");

            dtoList.add(StudentApplicationDetailDto.fromDomain(app, companyId, companyName, jobTitle));
        }

        // Sorting: default appliedAt desc
        boolean asc = sort != null && sort.toLowerCase().contains("asc");
        dtoList.sort(Comparator.comparing(
                StudentApplicationDetailDto::appliedAt,
                asc ? Comparator.nullsFirst(Comparator.naturalOrder()) : Comparator.nullsLast(Comparator.reverseOrder())
        ));

        int totalElements = dtoList.size();
        int totalPages = (int) Math.ceil((double) totalElements / boundedSize);
        int fromIndex = Math.min(boundedPage * boundedSize, totalElements);
        int toIndex = Math.min(fromIndex + boundedSize, totalElements);

        List<StudentApplicationDetailDto> pagedContent = (fromIndex < totalElements)
                ? dtoList.subList(fromIndex, toIndex)
                : Collections.emptyList();

        boolean last = (boundedPage >= totalPages - 1);
        PagedResponse<StudentApplicationDetailDto> response = new PagedResponse<>(
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

    /**
     * GET /api/v1/student/applications/{id}
     * Returns detailed information for a single application owned by the authenticated student.
     * Non-existent applications or applications belonging to other candidates return HTTP 404
     * to eliminate candidate data leakage and identifier enumeration.
     */
    @GetMapping("/applications/{id}")
    public ResponseEntity<StudentApplicationDetailDto> getApplicationById(@PathVariable("id") String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new ValidationException("Application ID cannot be null or empty.");
        }
        HireFlowUserPrincipal principal = resolveStudentPrincipal();
        String studentId = principal.getStudentId();
        String normAppId = applicationId.trim().toUpperCase();

        Application app = applicationService.getApplicationById(normAppId)
                .orElseThrow(() -> new EntityNotFoundException("Application with ID '" + normAppId + "' was not found."));

        // Strict ownership check: Must belong to current student!
        if (!app.getStudentId().equalsIgnoreCase(studentId)) {
            log.warn("[Security] Unauthorized cross-student application access attempt: student '{}' requested application '{}' owned by '{}'",
                    studentId, normAppId, app.getStudentId());
            // Return 404 to avoid disclosing existence of other students' applications
            throw new EntityNotFoundException("Application with ID '" + normAppId + "' was not found.");
        }

        RecruitmentDrive drive = driveService.getDriveById(app.getDriveId()).orElse(null);
        String jobTitle = drive != null ? drive.getJobTitle() : "Placement Drive";
        String companyId = drive != null ? drive.getCompanyId() : null;
        String companyName = Optional.ofNullable(companyId)
                .flatMap(companyRepository::findById)
                .map(Company::getName)
                .orElse("Corporate Partner");

        StudentApplicationDetailDto dto = StudentApplicationDetailDto.fromDomain(app, companyId, companyName, jobTitle);

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    /**
     * POST /api/v1/student/applications
     * Submits an application for a recruitment drive within a safe JDBC transaction.
     * Enforces eligibility, drive closure, application deadline, and duplicate rejection.
     * Authoritative student identity is derived exclusively from the session principal.
     */
    @PostMapping("/applications")
    public ResponseEntity<StudentApplicationDetailDto> submitApplication(
            @Valid @RequestBody ApplicationSubmitRequestDto requestDto,
            HttpServletRequest request) {

        HireFlowUserPrincipal principal = resolveStudentPrincipal();
        String studentId = principal.getStudentId();

        if (requestDto.driveId() == null || requestDto.driveId().trim().isEmpty()) {
            throw new ValidationException("Drive ID is required.");
        }
        String driveId = requestDto.driveId().trim().toUpperCase();

        // Transaction ownership: Managed explicitly by HireFlow JDBC TransactionManager.
        // Starts connection with autoCommit=false, commits on success, rolls back on error.
        Application created = transactionManager.runInTransaction(conn ->
                applicationService.submitApplication(studentId, driveId)
        );

        log.info("[StudentPortal] Application '{}' submitted successfully by student '{}' for drive '{}'",
                created.getId(), studentId, driveId);

        RecruitmentDrive drive = driveService.getDriveById(created.getDriveId()).orElse(null);
        String jobTitle = drive != null ? drive.getJobTitle() : "Placement Drive";
        String companyId = drive != null ? drive.getCompanyId() : null;
        String companyName = Optional.ofNullable(companyId)
                .flatMap(companyRepository::findById)
                .map(Company::getName)
                .orElse("Corporate Partner");

        StudentApplicationDetailDto dto = StudentApplicationDetailDto.fromDomain(created, companyId, companyName, jobTitle);
        URI location = URI.create("/api/v1/student/applications/" + created.getId());

        return ResponseEntity.created(location)
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    /**
     * GET /api/v1/student/dashboard
     * Returns a summary of candidate placement activities and recent applications.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<StudentDashboardDto> getDashboard() {
        HireFlowUserPrincipal principal = resolveStudentPrincipal();
        String studentId = principal.getStudentId();

        Student student = studentService.getStudentById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student record could not be found for ID: " + studentId));

        StudentProfileDto profileDto = StudentProfileDto.fromDomain(student);

        List<RecruitmentDrive> allDrives = driveService.getAllDrives();
        List<Application> studentApps = applicationService.getApplicationsForStudent(studentId);

        long totalEligibleDrives = allDrives.stream()
                .filter(d -> !d.isClosed() && !d.isDeadlinePassed(LocalDate.now()))
                .filter(d -> eligibilityService.checkEligibility(student, d).isEligible())
                .count();

        long totalApplications = studentApps.size();
        long shortlistedCount = studentApps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED)
                .count();

        Map<String, RecruitmentDrive> driveMap = allDrives.stream()
                .collect(Collectors.toMap(RecruitmentDrive::getId, Function.identity(), (d1, d2) -> d1));
        Map<String, Company> companyMap = companyRepository.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Function.identity(), (c1, c2) -> c1));

        List<StudentApplicationDetailDto> recentApps = studentApps.stream()
                .sorted(Comparator.comparing(Application::getAppliedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(app -> {
                    RecruitmentDrive drive = driveMap.get(app.getDriveId());
                    String jobTitle = drive != null ? drive.getJobTitle() : "Placement Drive";
                    String companyId = drive != null ? drive.getCompanyId() : null;
                    String companyName = Optional.ofNullable(companyId).map(companyMap::get).map(Company::getName).orElse("Corporate Partner");
                    return StudentApplicationDetailDto.fromDomain(app, companyId, companyName, jobTitle);
                })
                .toList();

        StudentDashboardDto dashboardDto = new StudentDashboardDto(
                profileDto,
                totalEligibleDrives,
                totalApplications,
                shortlistedCount,
                recentApps
        );

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dashboardDto);
    }

    /**
     * GET /api/v1/student/interviews
     * Returns confirmed interview appointments exclusively for the authenticated candidate.
     * Enforces candidate ownership isolation and strict anti-caching headers.
     */
    @GetMapping("/interviews")
    public ResponseEntity<List<StudentInterviewDto>> getStudentInterviews() {
        HireFlowUserPrincipal principal = resolveStudentPrincipal();
        String studentId = principal.getStudentId().trim().toUpperCase();

        Student student = studentService.getStudentById(studentId).orElse(null);
        String studentName = student != null ? student.getName() : principal.getDisplayName();
        String studentBranch = student != null ? student.getBranch() : "N/A";

        // Query confirmed sessions strictly for this student
        List<InterviewSession> confirmedSessions = (sessionRepository != null)
                ? sessionRepository.findByStudentId(studentId)
                : Collections.emptyList();

        // Fallback to student's applications if findByStudentId returned empty
        if (confirmedSessions.isEmpty() && sessionRepository != null) {
            List<Application> studentApps = applicationService.getApplicationsForStudent(studentId);
            confirmedSessions = studentApps.stream()
                    .flatMap(a -> sessionRepository.findByApplicationId(a.getId()).stream())
                    .sorted(Comparator.comparing(InterviewSession::getStartTime))
                    .toList();
        }

        // Batch lookups to prevent N+1 queries
        Map<String, Application> appMap = applicationService.getApplicationsForStudent(studentId).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity(), (a, b) -> a));
        Map<String, RecruitmentDrive> driveMap = driveService.getAllDrives().stream()
                .collect(Collectors.toMap(RecruitmentDrive::getId, Function.identity(), (a, b) -> a));
        Map<String, Company> companyMap = companyRepository.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Function.identity(), (a, b) -> a));
        Map<String, Room> roomMap = (roomRepository != null)
                ? roomRepository.findAll().stream().collect(Collectors.toMap(Room::getId, Function.identity(), (a, b) -> a))
                : Collections.emptyMap();
        Map<String, Interviewer> interviewerMap = (interviewerRepository != null)
                ? interviewerRepository.findAll().stream().collect(Collectors.toMap(Interviewer::getId, Function.identity(), (a, b) -> a))
                : Collections.emptyMap();

        List<StudentInterviewDto> dtos = new ArrayList<>();
        for (InterviewSession sess : confirmedSessions) {
            Application app = appMap.get(sess.getApplicationId());
            RecruitmentDrive drive = (app != null) ? driveMap.get(app.getDriveId()) : null;
            Company comp = (drive != null) ? companyMap.get(drive.getCompanyId()) : null;
            Room room = roomMap.get(sess.getRoomId());
            Interviewer interviewer = interviewerMap.get(sess.getInterviewerId());

            String jobTitle = (drive != null) ? drive.getJobTitle() : "Interview";
            String compName = (comp != null) ? comp.getName() : "Campus Recruiter";
            String driveId = (drive != null) ? drive.getId() : "N/A";
            String roomName = (room != null) ? room.getName() : sess.getRoomId();
            String interviewerName = (interviewer != null) ? interviewer.getName() : "Panel Member";
            String status = (app != null) ? app.getStatus().name() : ApplicationStatus.SCHEDULED.name();

            dtos.add(new StudentInterviewDto(
                    sess.getId(),
                    sess.getApplicationId(),
                    studentId,
                    studentName,
                    studentBranch,
                    driveId,
                    jobTitle,
                    compName,
                    sess.getInterviewerId(),
                    interviewerName,
                    sess.getRoomId(),
                    roomName,
                    sess.getStartTime(),
                    sess.getEndTime(),
                    status
            ));
        }

        dtos.sort(Comparator.comparing(StudentInterviewDto::startTime)
                .thenComparing(StudentInterviewDto::sessionId));

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dtos);
    }

    /**
     * GET /api/v1/student/interviews/{id}
     * Inspects an individual confirmed interview session owned by the authenticated student.
     * Non-existent sessions or sessions belonging to another student return HTTP 404 (non-disclosing).
     */
    @GetMapping("/interviews/{id}")
    public ResponseEntity<StudentInterviewDto> getStudentInterviewById(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Interview session ID cannot be null or blank.");
        }
        HireFlowUserPrincipal principal = resolveStudentPrincipal();
        String studentId = principal.getStudentId().trim().toUpperCase();
        String normId = id.trim().toUpperCase();

        if (sessionRepository == null) {
            throw new EntityNotFoundException("Interview session with ID '" + normId + "' was not found.");
        }

        InterviewSession sess = sessionRepository.findById(normId)
                .orElseThrow(() -> new EntityNotFoundException("Interview session with ID '" + normId + "' was not found."));

        // Strict ownership check: Must belong to current student!
        Application app = applicationService.getApplicationById(sess.getApplicationId())
                .orElseThrow(() -> new EntityNotFoundException("Interview session with ID '" + normId + "' was not found."));

        if (!app.getStudentId().equalsIgnoreCase(studentId)) {
            log.warn("[Security] Unauthorized cross-student interview access attempt: student '{}' requested session '{}' owned by '{}'",
                    studentId, normId, app.getStudentId());
            // Return 404 to avoid leaking existence of another student's interview session
            throw new EntityNotFoundException("Interview session with ID '" + normId + "' was not found.");
        }

        Student student = studentService.getStudentById(studentId).orElse(null);
        String studentName = student != null ? student.getName() : principal.getDisplayName();
        String studentBranch = student != null ? student.getBranch() : "N/A";

        RecruitmentDrive drive = driveService.getDriveById(app.getDriveId()).orElse(null);
        Company comp = (drive != null) ? companyRepository.findById(drive.getCompanyId()).orElse(null) : null;
        Room room = (roomRepository != null) ? roomRepository.findById(sess.getRoomId()).orElse(null) : null;
        Interviewer interviewer = (interviewerRepository != null) ? interviewerRepository.findById(sess.getInterviewerId()).orElse(null) : null;

        String jobTitle = (drive != null) ? drive.getJobTitle() : "Interview";
        String compName = (comp != null) ? comp.getName() : "Campus Recruiter";
        String driveId = (drive != null) ? drive.getId() : "N/A";
        String roomName = (room != null) ? room.getName() : sess.getRoomId();
        String interviewerName = (interviewer != null) ? interviewer.getName() : "Panel Member";
        String status = app.getStatus().name();

        StudentInterviewDto dto = new StudentInterviewDto(
                sess.getId(),
                sess.getApplicationId(),
                studentId,
                studentName,
                studentBranch,
                driveId,
                jobTitle,
                compName,
                sess.getInterviewerId(),
                interviewerName,
                sess.getRoomId(),
                roomName,
                sess.getStartTime(),
                sess.getEndTime(),
                status
        );

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }
}
