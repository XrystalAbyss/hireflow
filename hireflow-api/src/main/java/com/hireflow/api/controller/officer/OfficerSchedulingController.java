package com.hireflow.api.controller.officer;

import com.hireflow.api.dto.PagedResponse;
import com.hireflow.api.dto.scheduling.*;
import com.hireflow.api.security.HireFlowUserPrincipal;
import com.hireflow.api.service.SchedulingPreviewCacheService;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.*;
import com.hireflow.service.InterviewService;
import com.hireflow.web.dto.SchedulingPreviewState;
import com.hireflow.web.security.UserRole;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REST controller providing placement officer interview scheduling configuration,
 * advisory preview generation, secure transactional publication, and confirmed timetable viewing:
 * <ul>
 *     <li>POST /api/v1/officer/scheduling/preview   - Advisory scheduling preview (zero DB mutation)</li>
 *     <li>POST /api/v1/officer/scheduling/publish   - Transactional timetable publication reusing InterviewService</li>
 *     <li>GET  /api/v1/officer/scheduling/published - Confirmed interview timetable with drive/date filters</li>
 *     <li>GET  /api/v1/officer/scheduling/published/{id} - Confirmed individual interview session inspection</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/officer/scheduling")
public class OfficerSchedulingController {

    private static final Logger log = LoggerFactory.getLogger(OfficerSchedulingController.class);

    private static final HttpHeaders ANTI_CACHE_HEADERS;
    static {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0);
        ANTI_CACHE_HEADERS = HttpHeaders.readOnlyHttpHeaders(headers);
    }

    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final InterviewerRepository interviewerRepository;
    private final RoomRepository roomRepository;
    private final InterviewSessionRepository sessionRepository;
    private final CompanyRepository companyRepository;
    private final SchedulingEngine schedulingEngine;
    private final InterviewService interviewService;
    private final SchedulingPreviewCacheService previewCacheService;

    public OfficerSchedulingController(
            DriveRepository driveRepository,
            ApplicationRepository applicationRepository,
            StudentRepository studentRepository,
            InterviewerRepository interviewerRepository,
            RoomRepository roomRepository,
            InterviewSessionRepository sessionRepository,
            CompanyRepository companyRepository,
            SchedulingEngine schedulingEngine,
            InterviewService interviewService,
            SchedulingPreviewCacheService previewCacheService) {
        this.driveRepository = Objects.requireNonNull(driveRepository, "DriveRepository cannot be null.");
        this.applicationRepository = Objects.requireNonNull(applicationRepository, "ApplicationRepository cannot be null.");
        this.studentRepository = Objects.requireNonNull(studentRepository, "StudentRepository cannot be null.");
        this.interviewerRepository = Objects.requireNonNull(interviewerRepository, "InterviewerRepository cannot be null.");
        this.roomRepository = Objects.requireNonNull(roomRepository, "RoomRepository cannot be null.");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "InterviewSessionRepository cannot be null.");
        this.companyRepository = Objects.requireNonNull(companyRepository, "CompanyRepository cannot be null.");
        this.schedulingEngine = Objects.requireNonNull(schedulingEngine, "SchedulingEngine cannot be null.");
        this.interviewService = Objects.requireNonNull(interviewService, "InterviewService cannot be null.");
        this.previewCacheService = Objects.requireNonNull(previewCacheService, "SchedulingPreviewCacheService cannot be null.");
    }

    private HireFlowUserPrincipal resolveOfficerPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof HireFlowUserPrincipal principal)) {
            throw new AccessDeniedException("Full authentication with OFFICER or ADMINISTRATOR role is required.");
        }
        if (principal.getRole() != UserRole.PLACEMENT_OFFICER && principal.getRole() != UserRole.ADMINISTRATOR) {
            throw new AccessDeniedException("Access Denied: Officer operations require PLACEMENT_OFFICER or ADMINISTRATOR role.");
        }
        return principal;
    }

    /**
     * POST /api/v1/officer/scheduling/preview
     * Generates an advisory interview scheduling proposal using GreedyScheduler.
     * Guarantees zero database writes, zero application status changes, zero session inserts, and zero event broadcasts.
     */
    @PostMapping("/preview")
    public ResponseEntity<OfficerSchedulingPreviewResponseDto> generatePreview(
            @Valid @RequestBody OfficerSchedulingPreviewRequestDto requestDto) {

        HireFlowUserPrincipal principal = resolveOfficerPrincipal();
        String officerUsername = principal.getUsername();

        String driveId = requestDto.driveId().trim().toUpperCase();
        RecruitmentDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new EntityNotFoundException("Recruitment Drive '" + driveId + "' was not found."));

        if (!requestDto.startTime().isBefore(requestDto.endTime())) {
            throw new ValidationException(String.format(
                    "Window start time (%s) must be strictly before end time (%s).",
                    requestDto.startTime(), requestDto.endTime()));
        }

        int durationMins = requestDto.resolvedDurationMinutes();
        int bufferMins = requestDto.resolvedBufferMinutes();

        // 1. Authoritative Candidate Fetch: Only SHORTLISTED applications qualify
        List<Application> driveApps = applicationRepository.findByDriveId(driveId);
        List<Application> shortlistedApps = driveApps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED)
                .toList();

        if (shortlistedApps.isEmpty()) {
            throw new ValidationException(String.format(
                    "No candidate applications in SHORTLISTED status found for recruitment drive '%s' (%s). Shortlist candidates before scheduling.",
                    drive.getJobTitle(), driveId));
        }

        // 2. Resource pool resolution
        List<Interviewer> candidateInterviewers;
        if (requestDto.interviewerIds() != null && !requestDto.interviewerIds().isEmpty()) {
            Set<String> selectedIntIds = requestDto.interviewerIds().stream()
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            candidateInterviewers = interviewerRepository.findAll().stream()
                    .filter(i -> selectedIntIds.contains(i.getId()))
                    .toList();
        } else {
            candidateInterviewers = interviewerRepository.findByCompanyId(drive.getCompanyId());
            if (candidateInterviewers.isEmpty()) {
                candidateInterviewers = interviewerRepository.findAll();
            }
        }

        List<Room> candidateRooms;
        if (requestDto.roomIds() != null && !requestDto.roomIds().isEmpty()) {
            Set<String> selectedRoomIds = requestDto.roomIds().stream()
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            candidateRooms = roomRepository.findAll().stream()
                    .filter(r -> selectedRoomIds.contains(r.getId()))
                    .toList();
        } else {
            candidateRooms = roomRepository.findAll();
        }

        // 3. Existing confirmed sessions pool
        List<InterviewSession> existingSessions = sessionRepository.findAll();

        // 4. Construct SchedulingRequest
        TimeWindow window = new TimeWindow(
                LocalDateTime.of(requestDto.date(), requestDto.startTime()),
                LocalDateTime.of(requestDto.date(), requestDto.endTime())
        );

        SchedulingRequest schedRequest = SchedulingRequest.builder()
                .applications(shortlistedApps)
                .interviewers(candidateInterviewers)
                .rooms(candidateRooms)
                .schedulingWindow(window)
                .interviewDuration(Duration.ofMinutes(durationMins))
                .bufferDuration(Duration.ofMinutes(bufferMins))
                .existingSessions(existingSessions)
                .addDriveCompany(drive.getId(), drive.getCompanyId())
                .build();

        // 5. Delegate to GreedyScheduler (DB UNTOUCHED)
        SchedulingResult result = schedulingEngine.schedule(schedRequest);

        // 6. Enrich metadata for DTOs
        Map<String, Student> studentMap = studentRepository.findAll().stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        Map<String, Interviewer> interviewerMap = interviewerRepository.findAll().stream()
                .collect(Collectors.toMap(Interviewer::getId, Function.identity(), (a, b) -> a));
        Map<String, Room> roomMap = roomRepository.findAll().stream()
                .collect(Collectors.toMap(Room::getId, Function.identity(), (a, b) -> a));
        Map<String, Application> appMap = shortlistedApps.stream()
                .collect(Collectors.toMap(Application::getId, Function.identity(), (a, b) -> a));

        List<ProposedSessionDto> proposedDtos = new ArrayList<>();
        List<SchedulingPreviewState.ProposedSessionDto> stateProposedDtos = new ArrayList<>();
        for (InterviewSession sess : result.getScheduledSessions()) {
            Application app = appMap.get(sess.getApplicationId());
            String sId = (app != null) ? app.getStudentId() : "N/A";
            Student student = studentMap.get(sId);
            String sName = (student != null) ? student.getName() : sId;
            String sBranch = (student != null) ? student.getBranch() : "N/A";

            Interviewer interviewer = interviewerMap.get(sess.getInterviewerId());
            String iName = (interviewer != null) ? interviewer.getName() : sess.getInterviewerId();

            Room room = roomMap.get(sess.getRoomId());
            String rName = (room != null) ? room.getName() : sess.getRoomId();

            proposedDtos.add(new ProposedSessionDto(
                    sess.getId(),
                    sess.getApplicationId(),
                    sId,
                    sName,
                    sBranch,
                    sess.getInterviewerId(),
                    iName,
                    sess.getRoomId(),
                    rName,
                    sess.getStartTime(),
                    sess.getEndTime()
            ));

            stateProposedDtos.add(new SchedulingPreviewState.ProposedSessionDto(
                    sess.getId(),
                    sess.getApplicationId(),
                    sId,
                    sName,
                    sBranch,
                    sess.getInterviewerId(),
                    iName,
                    sess.getRoomId(),
                    rName,
                    sess.getStartTime(),
                    sess.getEndTime()
            ));
        }

        List<UnscheduledCandidateDto> unscheduledDtos = new ArrayList<>();
        List<SchedulingPreviewState.UnscheduledCandidateDto> stateUnscheduledDtos = new ArrayList<>();
        for (UnscheduledApplication unItem : result.getUnscheduledApplications()) {
            Application app = unItem.application();
            Student student = studentMap.get(app.getStudentId());
            String sName = (student != null) ? student.getName() : app.getStudentId();
            String sBranch = (student != null) ? student.getBranch() : "N/A";

            unscheduledDtos.add(new UnscheduledCandidateDto(
                    app.getId(),
                    app.getStudentId(),
                    sName,
                    sBranch,
                    unItem.reason()
            ));

            stateUnscheduledDtos.add(new SchedulingPreviewState.UnscheduledCandidateDto(
                    app.getId(),
                    app.getStudentId(),
                    sName,
                    sBranch,
                    unItem.reason()
            ));
        }

        String companyName = companyRepository.findById(drive.getCompanyId())
                .map(Company::getName)
                .orElse(drive.getCompanyId());

        // 7. Generate server-side preview state with 15-minute expiration
        String previewId = SchedulingPreviewState.generatePreviewId();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(SchedulingPreviewState.DEFAULT_MAX_AGE);

        SchedulingPreviewState previewState = new SchedulingPreviewState(
                previewId,
                officerUsername,
                now,
                expiresAt,
                false,
                drive.getId(),
                drive.getJobTitle(),
                companyName,
                requestDto.date(),
                requestDto.startTime(),
                requestDto.endTime(),
                durationMins,
                bufferMins,
                result.getTotalRequested(),
                result.getTotalScheduled(),
                result.getTotalUnscheduled(),
                stateProposedDtos,
                stateUnscheduledDtos
        );

        previewCacheService.put(previewState);

        log.info("[OfficerScheduling] Generated preview '{}' for drive '{}' by officer '{}': {} scheduled, {} unscheduled",
                SchedulingPreviewCacheService.maskToken(previewId), driveId, officerUsername, result.getTotalScheduled(), result.getTotalUnscheduled());

        OfficerSchedulingPreviewResponseDto responseDto = new OfficerSchedulingPreviewResponseDto(
                previewId,
                drive.getId(),
                drive.getJobTitle(),
                companyName,
                requestDto.date(),
                requestDto.startTime(),
                requestDto.endTime(),
                durationMins,
                bufferMins,
                expiresAt,
                result.getTotalRequested(),
                result.getTotalScheduled(),
                result.getTotalUnscheduled(),
                proposedDtos,
                unscheduledDtos
        );

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(responseDto);
    }

    /**
     * POST /api/v1/officer/scheduling/publish
     * Secure transactional publication of an advisory preview. Reuses InterviewService with per-session
     * atomic transactions, strict resource lock ordering, PostgreSQL exclusion constraint enforcement,
     * conditional status updates, and post-commit event dispatch.
     */
    @PostMapping("/publish")
    public ResponseEntity<OfficerPublicationSummaryDto> publishSchedule(
            @Valid @RequestBody OfficerSchedulingPublishRequestDto requestDto) {

        HireFlowUserPrincipal principal = resolveOfficerPrincipal();
        String officerUsername = principal.getUsername();

        // Atomically validate and consume preview token
        SchedulingPreviewState preview = previewCacheService.consume(requestDto.previewId(), officerUsername);

        if (preview.proposedSessions().isEmpty()) {
            throw new ValidationException("Cannot publish an empty timetable with 0 scheduled appointments.");
        }

        // Reconstruct domain InterviewSession objects exclusively from authoritative server preview
        List<InterviewSession> proposedSessions = new ArrayList<>();
        for (SchedulingPreviewState.ProposedSessionDto dto : preview.proposedSessions()) {
            proposedSessions.add(new InterviewSession(
                    dto.sessionId(),
                    dto.applicationId(),
                    dto.interviewerId(),
                    dto.roomId(),
                    dto.startTime(),
                    dto.endTime()
            ));
        }

        // Delegate to existing InterviewService publication engine
        InterviewService.PublicationResult pubResult = interviewService.publishSchedule(
                proposedSessions, Duration.ofMinutes(preview.bufferMinutes()));

        // Map published sessions into response details
        Map<String, SchedulingPreviewState.ProposedSessionDto> proposedBySessionId = preview.proposedSessions().stream()
                .collect(Collectors.toMap(SchedulingPreviewState.ProposedSessionDto::sessionId, Function.identity(), (a, b) -> a));

        List<PublishedSessionDetailDto> publishedDetails = new ArrayList<>();
        for (InterviewSession ps : pubResult.publishedSessions()) {
            SchedulingPreviewState.ProposedSessionDto dto = proposedBySessionId.get(ps.getId());
            String sId = dto != null ? dto.studentId() : "N/A";
            String sName = dto != null ? dto.studentName() : "N/A";
            String iName = dto != null ? dto.interviewerName() : ps.getInterviewerId();
            String rName = dto != null ? dto.roomName() : ps.getRoomId();

            publishedDetails.add(new PublishedSessionDetailDto(
                    ps.getId(),
                    ps.getApplicationId(),
                    sId,
                    sName,
                    iName,
                    rName,
                    ps.getStartTime(),
                    ps.getEndTime()
            ));
        }

        List<PublicationConflictDetailDto> conflictDetails = new ArrayList<>();
        List<String> infrastructureErrors = new ArrayList<>();

        for (String rejectionMsg : pubResult.conflictRejections()) {
            if (rejectionMsg.startsWith("Failed to publish appointment")) {
                infrastructureErrors.add(rejectionMsg);
            } else {
                String category = categorizeConflict(rejectionMsg);
                String appId = extractAppIdFromMessage(rejectionMsg);
                conflictDetails.add(new PublicationConflictDetailDto(appId, rejectionMsg, category));
            }
        }

        int totalProposals = proposedSessions.size();
        int publishedCount = pubResult.publishedCount();
        int duplicatesCount = pubResult.skippedDuplicateApplicationIds().size();
        int conflictsCount = conflictDetails.size();
        int errorsCount = infrastructureErrors.size();
        int unattemptedCount = Math.max(0, totalProposals - (publishedCount + duplicatesCount + conflictsCount + errorsCount));

        List<String> confirmedSessionIds = pubResult.publishedSessions().stream()
                .map(InterviewSession::getId)
                .toList();

        String attemptId = "PUB-ATTEMPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PublicationOutcome outcome;
        org.springframework.http.HttpStatus httpStatus;
        String recoveryInstructions;

        if (publishedCount + duplicatesCount == totalProposals && conflictsCount == 0 && errorsCount == 0) {
            outcome = PublicationOutcome.ALL_CONFIRMED;
            httpStatus = org.springframework.http.HttpStatus.OK;
            recoveryInstructions = "All proposed interview appointments were successfully confirmed and persisted. Students may view their confirmed timetables.";
        } else if (publishedCount > 0) {
            outcome = PublicationOutcome.PARTIAL_SUCCESS;
            httpStatus = org.springframework.http.HttpStatus.MULTI_STATUS;
            recoveryInstructions = String.format(
                    "Partial publication: %d of %d appointments confirmed. %d rejected due to conflicts. Confirmed sessions are durably committed in PostgreSQL. Please query GET /api/v1/officer/scheduling/published?driveId=%s to inspect confirmed sessions, and generate a fresh preview for remaining unconfirmed candidates.",
                    publishedCount, totalProposals, conflictsCount + errorsCount, preview.driveId());
        } else if (errorsCount > 0 && publishedCount == 0) {
            // Infrastructure / PostgreSQL outage: zero published because database connection/transaction failed.
            // Genuine PostgreSQL outages must return 5xx rather than being disguised as business conflicts (NONE_CONFIRMED).
            log.error("[OfficerScheduling] PostgreSQL infrastructure failure during timetable publication for preview '{}': {}",
                    SchedulingPreviewCacheService.maskToken(preview.previewId()), infrastructureErrors.get(0));
            throw new com.hireflow.exception.DatabaseException(
                    "PostgreSQL publication failure: " + infrastructureErrors.get(0));
        } else {
            outcome = PublicationOutcome.NONE_CONFIRMED;
            httpStatus = org.springframework.http.HttpStatus.CONFLICT;
            recoveryInstructions = String.format(
                    "Zero appointments confirmed out of %d proposals. All proposals encountered resource conflicts or stale application states. No database mutations occurred. Please resolve resource conflicts and generate a fresh preview.",
                    totalProposals);
        }

        OfficerPublicationSummaryDto summaryDto = new OfficerPublicationSummaryDto(
                attemptId,
                preview.previewId(),
                SchedulingPreviewCacheService.maskToken(preview.previewId()),
                preview.driveId(),
                preview.jobTitle(),
                preview.companyName(),
                outcome,
                recoveryInstructions,
                Instant.now(),
                totalProposals,
                publishedCount,
                duplicatesCount,
                conflictsCount,
                errorsCount,
                unattemptedCount,
                confirmedSessionIds,
                publishedDetails,
                pubResult.skippedDuplicateApplicationIds(),
                conflictDetails,
                infrastructureErrors
        );

        previewCacheService.recordPublicationOutcome(preview.previewId(), summaryDto);

        log.info("[OfficerScheduling] Published timetable '{}': outcome={}, {} confirmed, {} skipped duplicates, {} conflicts, {} errors",
                SchedulingPreviewCacheService.maskToken(preview.previewId()), outcome, summaryDto.publishedCount(), summaryDto.skippedDuplicatesCount(),
                summaryDto.conflictsCount(), summaryDto.errorsCount());

        return ResponseEntity.status(httpStatus)
                .headers(ANTI_CACHE_HEADERS)
                .body(summaryDto);
    }

    /**
     * GET /api/v1/officer/scheduling/published
     * Retrieves confirmed interview timetable directly from PostgreSQL with deterministic sorting and bounded pagination.
     */
    @GetMapping("/published")
    public ResponseEntity<PagedResponse<OfficerInterviewDetailDto>> getPublishedSchedule(
            @RequestParam(name = "driveId", required = false) String driveId,
            @RequestParam(name = "companyId", required = false) String companyId,
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        resolveOfficerPrincipal();

        int boundedPage = Math.max(0, page);
        int boundedSize = Math.clamp(size, 1, 100);

        List<InterviewSession> allConfirmed = sessionRepository.findAll();

        // Batch lookups to prevent N+1 queries
        Map<String, Application> appMap = applicationRepository.findAll().stream()
                .collect(Collectors.toMap(Application::getId, Function.identity(), (a, b) -> a));
        Map<String, Student> studentMap = studentRepository.findAll().stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        Map<String, Interviewer> interviewerMap = interviewerRepository.findAll().stream()
                .collect(Collectors.toMap(Interviewer::getId, Function.identity(), (a, b) -> a));
        Map<String, Room> roomMap = roomRepository.findAll().stream()
                .collect(Collectors.toMap(Room::getId, Function.identity(), (a, b) -> a));
        Map<String, RecruitmentDrive> driveMap = driveRepository.findAll().stream()
                .collect(Collectors.toMap(RecruitmentDrive::getId, Function.identity(), (a, b) -> a));
        Map<String, Company> companyMap = companyRepository.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Function.identity(), (a, b) -> a));

        List<OfficerInterviewDetailDto> filtered = new ArrayList<>();
        for (InterviewSession sess : allConfirmed) {
            Application app = appMap.get(sess.getApplicationId());
            RecruitmentDrive drive = (app != null) ? driveMap.get(app.getDriveId()) : null;
            Company comp = (drive != null) ? companyMap.get(drive.getCompanyId()) : null;

            // Apply filters
            if (driveId != null && !driveId.isBlank()) {
                if (drive == null || !drive.getId().equalsIgnoreCase(driveId.trim())) {
                    continue;
                }
            }
            if (companyId != null && !companyId.isBlank()) {
                if (comp == null || !comp.getId().equalsIgnoreCase(companyId.trim())) {
                    continue;
                }
            }
            if (date != null) {
                if (!sess.getStartTime().toLocalDate().equals(date)) {
                    continue;
                }
            }

            Student student = (app != null) ? studentMap.get(app.getStudentId()) : null;
            Interviewer interviewer = interviewerMap.get(sess.getInterviewerId());
            Room room = roomMap.get(sess.getRoomId());

            String sId = (app != null) ? app.getStudentId() : "N/A";
            String sName = (student != null) ? student.getName() : sId;
            String sBranch = (student != null) ? student.getBranch() : "N/A";
            String dId = (drive != null) ? drive.getId() : "N/A";
            String jTitle = (drive != null) ? drive.getJobTitle() : "Interview";
            String cId = (comp != null) ? comp.getId() : "N/A";
            String cName = (comp != null) ? comp.getName() : "Campus Recruiter";
            String iName = (interviewer != null) ? interviewer.getName() : sess.getInterviewerId();
            String rName = (room != null) ? room.getName() : sess.getRoomId();
            String status = (app != null) ? app.getStatus().name() : ApplicationStatus.SCHEDULED.name();

            filtered.add(new OfficerInterviewDetailDto(
                    sess.getId(),
                    sess.getApplicationId(),
                    sId,
                    sName,
                    sBranch,
                    dId,
                    jTitle,
                    cId,
                    cName,
                    sess.getInterviewerId(),
                    iName,
                    sess.getRoomId(),
                    rName,
                    sess.getStartTime(),
                    sess.getEndTime(),
                    status
            ));
        }

        // Deterministic sorting: startTime asc, id asc
        filtered.sort(Comparator.comparing(OfficerInterviewDetailDto::startTime)
                .thenComparing(OfficerInterviewDetailDto::sessionId));

        int totalElements = filtered.size();
        int totalPages = (int) Math.ceil((double) totalElements / boundedSize);
        int fromIndex = Math.min(boundedPage * boundedSize, totalElements);
        int toIndex = Math.min(fromIndex + boundedSize, totalElements);

        List<OfficerInterviewDetailDto> pagedContent = (fromIndex < totalElements)
                ? filtered.subList(fromIndex, toIndex)
                : Collections.emptyList();

        boolean last = (boundedPage >= totalPages - 1);
        PagedResponse<OfficerInterviewDetailDto> response = new PagedResponse<>(
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
     * GET /api/v1/officer/scheduling/published/{id}
     * Inspects a single confirmed interview appointment by ID.
     */
    @GetMapping("/published/{id}")
    public ResponseEntity<OfficerInterviewDetailDto> getPublishedSessionById(@PathVariable("id") String id) {
        resolveOfficerPrincipal();

        if (id == null || id.isBlank()) {
            throw new ValidationException("Interview session ID cannot be null or blank.");
        }
        String normId = id.trim().toUpperCase();

        InterviewSession sess = sessionRepository.findById(normId)
                .orElseThrow(() -> new EntityNotFoundException("Interview session with ID '" + normId + "' was not found."));

        Application app = applicationRepository.findById(sess.getApplicationId()).orElse(null);
        Student student = (app != null) ? studentRepository.findById(app.getStudentId()).orElse(null) : null;
        RecruitmentDrive drive = (app != null) ? driveRepository.findById(app.getDriveId()).orElse(null) : null;
        Company comp = (drive != null) ? companyRepository.findById(drive.getCompanyId()).orElse(null) : null;
        Interviewer interviewer = interviewerRepository.findById(sess.getInterviewerId()).orElse(null);
        Room room = roomRepository.findById(sess.getRoomId()).orElse(null);

        String sId = (app != null) ? app.getStudentId() : "N/A";
        String sName = (student != null) ? student.getName() : sId;
        String sBranch = (student != null) ? student.getBranch() : "N/A";
        String dId = (drive != null) ? drive.getId() : "N/A";
        String jTitle = (drive != null) ? drive.getJobTitle() : "Interview";
        String cId = (comp != null) ? comp.getId() : "N/A";
        String cName = (comp != null) ? comp.getName() : "Campus Recruiter";
        String iName = (interviewer != null) ? interviewer.getName() : sess.getInterviewerId();
        String rName = (room != null) ? room.getName() : sess.getRoomId();
        String status = (app != null) ? app.getStatus().name() : ApplicationStatus.SCHEDULED.name();

        OfficerInterviewDetailDto dto = new OfficerInterviewDetailDto(
                sess.getId(),
                sess.getApplicationId(),
                sId,
                sName,
                sBranch,
                dId,
                jTitle,
                cId,
                cName,
                sess.getInterviewerId(),
                iName,
                sess.getRoomId(),
                rName,
                sess.getStartTime(),
                sess.getEndTime(),
                status
        );

        return ResponseEntity.ok()
                .headers(ANTI_CACHE_HEADERS)
                .body(dto);
    }

    private String categorizeConflict(String msg) {
        if (msg == null) return "GENERAL";
        if (msg.contains("Student conflict")) return "STUDENT_CONFLICT";
        if (msg.contains("Interviewer conflict")) return "INTERVIEWER_CONFLICT";
        if (msg.contains("Room conflict")) return "ROOM_CONFLICT";
        if (msg.contains("SHORTLISTED") || msg.contains("status")) return "STATUS_CONFLICT";
        return "GENERAL";
    }

    private String extractAppIdFromMessage(String msg) {
        if (msg == null) return "N/A";
        int start = msg.indexOf('\'');
        int end = (start >= 0) ? msg.indexOf('\'', start + 1) : -1;
        if (start >= 0 && end > start) {
            return msg.substring(start + 1, end);
        }
        return "N/A";
    }
}
