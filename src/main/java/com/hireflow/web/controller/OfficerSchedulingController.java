package com.hireflow.web.controller;

import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.*;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.InterviewService;
import com.hireflow.web.dto.ConfirmedInterviewViewItem;
import com.hireflow.web.dto.PublicationSummaryState;
import com.hireflow.web.dto.SchedulingPreviewState;
import com.hireflow.web.dto.SchedulingPreviewState.ProposedSessionDto;
import com.hireflow.web.dto.SchedulingPreviewState.UnscheduledCandidateDto;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.listener.WebContextListener;
import com.hireflow.web.security.CsrfTokenManager;
import com.hireflow.web.security.UserPrincipal;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller handling placement officer interview scheduling configuration, preview generation,
 * secure transactional publication, and confirmed timetable viewing:
 * <ul>
 *     <li>GET  /officer/scheduling           - Display scheduling configuration form</li>
 *     <li>POST /officer/scheduling/preview   - Validate parameters, run GreedyScheduler, and store server preview</li>
 *     <li>GET  /officer/scheduling/preview   - Display active advisory timetable stored in session</li>
 *     <li>POST /officer/scheduling/publish   - Secure transactional publication delegating to Stage 2 InterviewService</li>
 *     <li>GET  /officer/scheduling/published - Persistent confirmed interview timetable with drive/date filters</li>
 * </ul>
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Officer authorization and valid CSRF token strictly required.</li>
 *     <li>Cryptographically unpredictable preview identifiers with 15-minute expiration.</li>
 *     <li>Multi-tab isolation via session-scoped bounded preview state map.</li>
 *     <li>Anti-replay enforcement: previews are consumed exactly once.</li>
 *     <li>Authoritative server-side validation: browser never supplies candidate or room assignments.</li>
 *     <li>Stage 2 per-session transactional integrity with lock ordering and GiST protection.</li>
 * </ul>
 */
public class OfficerSchedulingController extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(OfficerSchedulingController.class.getName());
    public static final String PREVIEW_SESSION_ATTR = "hireflow.schedulingPreview";
    public static final String PREVIEWS_MAP_SESSION_ATTR = "hireflow.schedulingPreviews";
    public static final String PUBLICATION_RESULT_SESSION_ATTR = "hireflow.lastPublicationResult";
    private static final int MAX_PREVIEWS_PER_SESSION = 10;

    private static final String FORM_VIEW = "/WEB-INF/views/officer/scheduling-form.jsp";
    private static final String PREVIEW_VIEW = "/WEB-INF/views/officer/scheduling-preview.jsp";
    private static final String PUBLISHED_VIEW = "/WEB-INF/views/officer/published-schedule.jsp";

    private DriveRepository driveRepository;
    private ApplicationRepository applicationRepository;
    private StudentRepository studentRepository;
    private InterviewerRepository interviewerRepository;
    private RoomRepository roomRepository;
    private InterviewSessionRepository sessionRepository;
    private CompanyRepository companyRepository;
    private SchedulingEngine schedulingEngine;
    private InterviewService interviewService;

    public OfficerSchedulingController() {
    }

    public OfficerSchedulingController(DriveRepository driveRepository,
                                       ApplicationRepository applicationRepository,
                                       StudentRepository studentRepository,
                                       InterviewerRepository interviewerRepository,
                                       RoomRepository roomRepository,
                                       InterviewSessionRepository sessionRepository,
                                       CompanyRepository companyRepository,
                                       SchedulingEngine schedulingEngine) {
        this(driveRepository, applicationRepository, studentRepository, interviewerRepository,
                roomRepository, sessionRepository, companyRepository, schedulingEngine, null);
    }

    public OfficerSchedulingController(DriveRepository driveRepository,
                                       ApplicationRepository applicationRepository,
                                       StudentRepository studentRepository,
                                       InterviewerRepository interviewerRepository,
                                       RoomRepository roomRepository,
                                       InterviewSessionRepository sessionRepository,
                                       CompanyRepository companyRepository,
                                       SchedulingEngine schedulingEngine,
                                       InterviewService interviewService) {
        this.driveRepository = driveRepository;
        this.applicationRepository = applicationRepository;
        this.studentRepository = studentRepository;
        this.interviewerRepository = interviewerRepository;
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.companyRepository = companyRepository;
        this.schedulingEngine = schedulingEngine != null ? schedulingEngine : new GreedyScheduler();
        this.interviewService = interviewService;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        resolveDependencies();
    }

    private void resolveDependencies() {
        ServletConfig config = getServletConfig();
        if (config != null) {
            ServletContext sc = config.getServletContext();
            if (sc != null) {
                if (this.driveRepository == null) {
                    this.driveRepository = (DriveRepository) sc.getAttribute(WebContextListener.DRIVE_REPO_ATTR);
                }
                if (this.applicationRepository == null) {
                    this.applicationRepository = (ApplicationRepository) sc.getAttribute(WebContextListener.APPLICATION_REPO_ATTR);
                }
                if (this.studentRepository == null) {
                    this.studentRepository = (StudentRepository) sc.getAttribute(WebContextListener.STUDENT_REPO_ATTR);
                }
                if (this.interviewerRepository == null) {
                    this.interviewerRepository = (InterviewerRepository) sc.getAttribute(WebContextListener.INTERVIEWER_REPO_ATTR);
                }
                if (this.roomRepository == null) {
                    this.roomRepository = (RoomRepository) sc.getAttribute(WebContextListener.ROOM_REPO_ATTR);
                }
                if (this.sessionRepository == null) {
                    this.sessionRepository = (InterviewSessionRepository) sc.getAttribute(WebContextListener.SESSION_REPO_ATTR);
                }
                if (this.companyRepository == null) {
                    this.companyRepository = (CompanyRepository) sc.getAttribute(WebContextListener.COMPANY_REPO_ATTR);
                }
                if (this.schedulingEngine == null) {
                    Object se = sc.getAttribute(WebContextListener.SCHEDULING_ENGINE_ATTR);
                    this.schedulingEngine = (se instanceof SchedulingEngine engine) ? engine : new GreedyScheduler();
                }
                if (this.interviewService == null) {
                    this.interviewService = (InterviewService) sc.getAttribute(WebContextListener.INTERVIEW_SERVICE_ATTR);
                }
            }
        }
    }

    private InterviewService resolveInterviewService() {
        if (this.interviewService != null) return this.interviewService;
        resolveDependencies();
        if (this.interviewService != null) return this.interviewService;

        if (this.sessionRepository != null && this.applicationRepository != null) {
            ApplicationService appSvc = new ApplicationService(
                    this.applicationRepository,
                    this.studentRepository != null ? this.studentRepository : new InMemoryStudentRepository(),
                    this.driveRepository != null ? this.driveRepository : new InMemoryDriveRepository(),
                    new EligibilityService(),
                    this.sessionRepository
            );
            this.interviewService = new InterviewService(this.sessionRepository, this.applicationRepository, appSvc);
            return this.interviewService;
        }
        throw new IllegalStateException("InterviewService could not be resolved from ServletContext or repositories.");
    }

    private UserPrincipal verifyOfficer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        UserPrincipal principal = (session != null)
                ? (UserPrincipal) session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)
                : null;
        if (principal == null || !principal.isPlacementOfficer()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Officer account required.");
            return null;
        }
        return principal;
    }

    private void setAntiCacheHeaders(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
    }

    private boolean isValidOrigin(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin != null && !origin.trim().isEmpty()) {
            try {
                URI originUri = URI.create(origin);
                String originHost = originUri.getHost();
                String serverName = req.getServerName();
                return originHost != null && (originHost.equalsIgnoreCase(serverName) || "localhost".equalsIgnoreCase(originHost) || "127.0.0.1".equals(originHost));
            } catch (Exception ex) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, SchedulingPreviewState> getOrCreatePreviewsMap(HttpSession session) {
        Object attr = session.getAttribute(PREVIEWS_MAP_SESSION_ATTR);
        if (attr instanceof Map<?, ?> map) {
            return (Map<String, SchedulingPreviewState>) map;
        }
        Map<String, SchedulingPreviewState> map = new LinkedHashMap<String, SchedulingPreviewState>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SchedulingPreviewState> eldest) {
                return size() > MAX_PREVIEWS_PER_SESSION;
            }
        };
        session.setAttribute(PREVIEWS_MAP_SESSION_ATTR, map);
        return map;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal officer = verifyOfficer(req, resp);
        if (officer == null) return;

        resolveDependencies();
        HttpSession session = req.getSession(true);
        String csrfToken = CsrfTokenManager.getOrCreateToken(session);
        req.setAttribute("csrfToken", csrfToken);
        req.setAttribute("user", officer);

        String pathInfo = req.getPathInfo();

        // 1. Route: /officer/scheduling/preview (GET cached preview)
        if ("/preview".equalsIgnoreCase(pathInfo)) {
            String previewId = req.getParameter("previewId");
            SchedulingPreviewState preview = null;
            synchronized (session) {
                Map<String, SchedulingPreviewState> previewsMap = getOrCreatePreviewsMap(session);
                if (previewId != null && !previewId.trim().isEmpty()) {
                    preview = previewsMap.get(previewId.trim().toUpperCase());
                }
                if (preview == null) {
                    Object fallback = session.getAttribute(PREVIEW_SESSION_ATTR);
                    if (fallback instanceof SchedulingPreviewState fps) {
                        if (previewId == null || previewId.trim().isEmpty() || fps.previewId().equalsIgnoreCase(previewId.trim())) {
                            preview = fps;
                        }
                    }
                }
            }

            if (preview == null) {
                session.setAttribute("flash_error", "No active scheduling preview found. Please configure and generate a schedule preview.");
                resp.sendRedirect(req.getContextPath() + "/officer/scheduling");
                return;
            }

            if (!preview.officerUsername().equalsIgnoreCase(officer.getName())) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Preview belongs to another officer session.");
                return;
            }

            if (preview.isExpired()) {
                session.setAttribute("flash_error", "The scheduling preview has expired (15-minute validity). Please generate a fresh preview.");
                resp.sendRedirect(req.getContextPath() + "/officer/scheduling?driveId=" + preview.driveId());
                return;
            }

            req.setAttribute("preview", preview);
            req.setAttribute("pageTitle", "Proposed Interview Timetable Preview - HireFlow");
            req.setAttribute("activeNav", "officer-scheduling");
            req.getRequestDispatcher(PREVIEW_VIEW).forward(req, resp);
            return;
        }

        // 2. Route: /officer/scheduling/published (GET confirmed interview timetable)
        if ("/published".equalsIgnoreCase(pathInfo)) {
            renderPublishedTimetable(req, resp, officer, session);
            return;
        }

        // 3. Route: /officer/scheduling (Form)
        List<RecruitmentDrive> drives = (driveRepository != null) ? driveRepository.findAll() : List.of();
        List<Interviewer> interviewers = (interviewerRepository != null) ? interviewerRepository.findAll() : List.of();
        List<Room> rooms = (roomRepository != null) ? roomRepository.findAll() : List.of();

        Map<String, Company> companyMap = (companyRepository != null)
                ? companyRepository.findAll().stream().collect(Collectors.toMap(Company::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        Map<String, Long> shortlistedCounts = new HashMap<>();
        if (applicationRepository != null) {
            for (RecruitmentDrive d : drives) {
                long count = applicationRepository.findByDriveId(d.getId()).stream()
                        .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED)
                        .count();
                shortlistedCounts.put(d.getId(), count);
            }
        }

        String selectedDriveId = req.getParameter("driveId");
        if (selectedDriveId == null || selectedDriveId.trim().isEmpty()) {
            selectedDriveId = drives.stream()
                    .filter(d -> shortlistedCounts.getOrDefault(d.getId(), 0L) > 0)
                    .map(RecruitmentDrive::getId)
                    .findFirst()
                    .orElse(drives.isEmpty() ? null : drives.get(0).getId());
        } else {
            selectedDriveId = selectedDriveId.trim().toUpperCase();
        }

        req.setAttribute("drives", drives);
        req.setAttribute("interviewers", interviewers);
        req.setAttribute("rooms", rooms);
        req.setAttribute("companyMap", companyMap);
        req.setAttribute("shortlistedCounts", shortlistedCounts);
        req.setAttribute("selectedDriveId", selectedDriveId);

        LocalDate defaultDate = LocalDate.now().plusDays(1);
        req.setAttribute("defaultDate", defaultDate.toString());
        req.setAttribute("defaultStartTime", "09:00");
        req.setAttribute("defaultEndTime", "17:00");
        req.setAttribute("defaultDuration", 45);
        req.setAttribute("defaultBuffer", 15);

        req.setAttribute("pageTitle", "Automated Interview Scheduling - HireFlow");
        req.setAttribute("activeNav", "officer-scheduling");
        req.getRequestDispatcher(FORM_VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal officer = verifyOfficer(req, resp);
        if (officer == null) return;

        if (!CsrfTokenManager.validateToken(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF security token.");
            return;
        }

        if (!isValidOrigin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-origin request blocked by security policy.");
            return;
        }

        resolveDependencies();
        HttpSession session = req.getSession(true);
        String pathInfo = req.getPathInfo();

        if ("/preview".equalsIgnoreCase(pathInfo)) {
            handleGeneratePreview(req, resp, officer, session);
            return;
        } else if ("/publish".equalsIgnoreCase(pathInfo)) {
            handlePublishSchedule(req, resp, officer, session);
            return;
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found.");
        }
    }

    private void handleGeneratePreview(HttpServletRequest req, HttpServletResponse resp,
                                       UserPrincipal officer, HttpSession session)
            throws ServletException, IOException {

        String driveId = req.getParameter("driveId");
        String dateStr = req.getParameter("date");
        String startTimeStr = req.getParameter("startTime");
        String endTimeStr = req.getParameter("endTime");
        String durationStr = req.getParameter("duration");
        String bufferStr = req.getParameter("buffer");
        String[] interviewerIds = req.getParameterValues("interviewerIds");
        String[] roomIds = req.getParameterValues("roomIds");

        if (driveId == null || driveId.trim().isEmpty()) {
            session.setAttribute("flash_error", "Please select a recruitment drive.");
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling");
            return;
        }
        String normDriveId = driveId.trim().toUpperCase();

        RecruitmentDrive drive = (driveRepository != null) ? driveRepository.findById(normDriveId).orElse(null) : null;
        if (drive == null) {
            session.setAttribute("flash_error", "Selected Recruitment Drive '" + normDriveId + "' not found.");
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling");
            return;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr != null ? dateStr.trim() : "");
        } catch (DateTimeParseException ex) {
            session.setAttribute("flash_error", "Invalid date format. Expected YYYY-MM-DD.");
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling?driveId=" + normDriveId);
            return;
        }

        LocalTime startTime;
        LocalTime endTime;
        try {
            startTime = LocalTime.parse(startTimeStr != null ? startTimeStr.trim() : "");
            endTime = LocalTime.parse(endTimeStr != null ? endTimeStr.trim() : "");
        } catch (DateTimeParseException ex) {
            session.setAttribute("flash_error", "Invalid time format. Expected HH:MM (e.g. 09:00).");
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling?driveId=" + normDriveId);
            return;
        }

        if (!startTime.isBefore(endTime)) {
            session.setAttribute("flash_error", "Window start time (" + startTime + ") must be strictly before end time (" + endTime + ").");
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling?driveId=" + normDriveId);
            return;
        }

        int durationMins;
        int bufferMins;
        try {
            durationMins = Integer.parseInt(durationStr != null ? durationStr.trim() : "45");
            bufferMins = Integer.parseInt(bufferStr != null ? bufferStr.trim() : "15");
            if (durationMins <= 0 || durationMins > 240) {
                throw new ValidationException("Interview duration must be between 1 and 240 minutes.");
            }
            if (bufferMins < 0 || bufferMins > 120) {
                throw new ValidationException("Buffer duration must be between 0 and 120 minutes.");
            }
        } catch (Exception ex) {
            session.setAttribute("flash_error", "Invalid duration or buffer parameters: " + ex.getMessage());
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling?driveId=" + normDriveId);
            return;
        }

        // 1. Authoritative Candidate Fetch: Only SHORTLISTED applications qualify
        List<Application> driveApps = (applicationRepository != null) ? applicationRepository.findByDriveId(normDriveId) : List.of();
        List<Application> shortlistedApps = driveApps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED)
                .toList();

        if (shortlistedApps.isEmpty()) {
            session.setAttribute("flash_error", String.format(
                    "No candidate applications in SHORTLISTED status found for recruitment drive '%s' (%s). Shortlist candidates before scheduling.",
                    drive.getJobTitle(), normDriveId));
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling?driveId=" + normDriveId);
            return;
        }

        // 2. Resource pool resolution
        List<Interviewer> candidateInterviewers;
        if (interviewerIds != null && interviewerIds.length > 0) {
            Set<String> selectedIntIds = Arrays.stream(interviewerIds)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            candidateInterviewers = (interviewerRepository != null)
                    ? interviewerRepository.findAll().stream().filter(i -> selectedIntIds.contains(i.getId())).toList()
                    : List.of();
        } else {
            candidateInterviewers = (interviewerRepository != null)
                    ? interviewerRepository.findByCompanyId(drive.getCompanyId())
                    : List.of();
            if (candidateInterviewers.isEmpty() && interviewerRepository != null) {
                candidateInterviewers = interviewerRepository.findAll();
            }
        }

        List<Room> candidateRooms;
        if (roomIds != null && roomIds.length > 0) {
            Set<String> selectedRoomIds = Arrays.stream(roomIds)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            candidateRooms = (roomRepository != null)
                    ? roomRepository.findAll().stream().filter(r -> selectedRoomIds.contains(r.getId())).toList()
                    : List.of();
        } else {
            candidateRooms = (roomRepository != null) ? roomRepository.findAll() : List.of();
        }

        // 3. Existing confirmed sessions pool
        List<InterviewSession> existingSessions = (sessionRepository != null) ? sessionRepository.findAll() : List.of();

        // 4. Construct SchedulingRequest
        TimeWindow window = new TimeWindow(
                LocalDateTime.of(date, startTime),
                LocalDateTime.of(date, endTime)
        );

        SchedulingRequest request = SchedulingRequest.builder()
                .applications(shortlistedApps)
                .interviewers(candidateInterviewers)
                .rooms(candidateRooms)
                .schedulingWindow(window)
                .interviewDuration(Duration.ofMinutes(durationMins))
                .bufferDuration(Duration.ofMinutes(bufferMins))
                .existingSessions(existingSessions)
                .addDriveCompany(drive.getId(), drive.getCompanyId())
                .build();

        // 5. Delegate scheduling to GreedyScheduler (DB UNTOUCHED)
        SchedulingEngine engine = (this.schedulingEngine != null) ? this.schedulingEngine : new GreedyScheduler();
        SchedulingResult result = engine.schedule(request);

        // 6. Enrich metadata for display DTOs
        Map<String, Student> studentMap = (studentRepository != null)
                ? studentRepository.findAll().stream().collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, Interviewer> interviewerMap = (interviewerRepository != null)
                ? interviewerRepository.findAll().stream().collect(Collectors.toMap(Interviewer::getId, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, Room> roomLookup = (roomRepository != null)
                ? roomRepository.findAll().stream().collect(Collectors.toMap(Room::getId, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, Application> appLookup = shortlistedApps.stream()
                .collect(Collectors.toMap(Application::getId, Function.identity(), (a, b) -> a));

        List<ProposedSessionDto> proposedDtos = new ArrayList<>();
        for (InterviewSession sessionItem : result.getScheduledSessions()) {
            Application app = appLookup.get(sessionItem.getApplicationId());
            String sId = (app != null) ? app.getStudentId() : "N/A";
            Student student = studentMap.get(sId);
            String sName = (student != null) ? student.getName() : sId;
            String sBranch = (student != null) ? student.getBranch() : "N/A";

            Interviewer interviewer = interviewerMap.get(sessionItem.getInterviewerId());
            String iName = (interviewer != null) ? interviewer.getName() : sessionItem.getInterviewerId();

            Room room = roomLookup.get(sessionItem.getRoomId());
            String rName = (room != null) ? room.getName() : sessionItem.getRoomId();

            proposedDtos.add(new ProposedSessionDto(
                    sessionItem.getId(),
                    sessionItem.getApplicationId(),
                    sId,
                    sName,
                    sBranch,
                    sessionItem.getInterviewerId(),
                    iName,
                    sessionItem.getRoomId(),
                    rName,
                    sessionItem.getStartTime(),
                    sessionItem.getEndTime()
            ));
        }

        List<UnscheduledCandidateDto> unscheduledDtos = new ArrayList<>();
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
        }

        String companyName = (companyRepository != null && companyRepository.findById(drive.getCompanyId()).isPresent())
                ? companyRepository.findById(drive.getCompanyId()).get().getName()
                : drive.getCompanyId();

        // 7. Construct cryptographically secure, bounded preview state with 15-minute expiration
        String previewId = SchedulingPreviewState.generatePreviewId();
        Instant now = Instant.now();
        SchedulingPreviewState previewState = new SchedulingPreviewState(
                previewId,
                officer.getName(),
                now,
                now.plus(SchedulingPreviewState.DEFAULT_MAX_AGE),
                false,
                drive.getId(),
                drive.getJobTitle(),
                companyName,
                date,
                startTime,
                endTime,
                durationMins,
                bufferMins,
                result.getTotalRequested(),
                result.getTotalScheduled(),
                result.getTotalUnscheduled(),
                proposedDtos,
                unscheduledDtos
        );

        // Store bounded preview in multi-tab session map and fallback single slot
        synchronized (session) {
            Map<String, SchedulingPreviewState> previewsMap = getOrCreatePreviewsMap(session);
            previewsMap.put(previewId, previewState);
            session.setAttribute(PREVIEW_SESSION_ATTR, previewState);
        }

        LOGGER.info(String.format("[OfficerScheduling] Generated advisory timetable preview %s for drive %s: %d scheduled, %d unscheduled",
                previewId, normDriveId, result.getTotalScheduled(), result.getTotalUnscheduled()));

        req.setAttribute("preview", previewState);
        req.setAttribute("csrfToken", CsrfTokenManager.getOrCreateToken(session));
        req.setAttribute("user", officer);
        req.setAttribute("pageTitle", "Proposed Interview Timetable Preview - HireFlow");
        req.setAttribute("activeNav", "officer-scheduling");
        req.getRequestDispatcher(PREVIEW_VIEW).forward(req, resp);
    }

    private void handlePublishSchedule(HttpServletRequest req, HttpServletResponse resp,
                                       UserPrincipal officer, HttpSession session)
            throws IOException {

        String previewId = req.getParameter("previewId");
        if (previewId == null || previewId.trim().isEmpty()) {
            session.setAttribute("flash_error", "Missing preview identifier. Please generate a schedule preview before publishing.");
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling");
            return;
        }
        String normPreviewId = previewId.trim().toUpperCase();

        SchedulingPreviewState preview;
        synchronized (session) {
            Map<String, SchedulingPreviewState> previewsMap = getOrCreatePreviewsMap(session);
            preview = previewsMap.get(normPreviewId);
            if (preview == null) {
                Object fallback = session.getAttribute(PREVIEW_SESSION_ATTR);
                if (fallback instanceof SchedulingPreviewState fps && fps.previewId().equalsIgnoreCase(normPreviewId)) {
                    preview = fps;
                }
            }

            if (preview == null) {
                session.setAttribute("flash_error", "Scheduling preview '" + normPreviewId + "' not found or session has expired. Please generate a fresh preview.");
                resp.sendRedirect(req.getContextPath() + "/officer/scheduling");
                return;
            }

            if (!preview.officerUsername().equalsIgnoreCase(officer.getName())) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Preview belongs to another officer account.");
                return;
            }

            if (preview.isConsumed()) {
                session.setAttribute("flash_error", "Scheduling preview '" + normPreviewId + "' has already been published. Replay prevented.");
                resp.sendRedirect(req.getContextPath() + "/officer/scheduling/published?driveId=" + preview.driveId());
                return;
            }

            if (preview.isExpired()) {
                session.setAttribute("flash_error", "Scheduling preview '" + normPreviewId + "' has expired (15-minute validity). Please generate a fresh preview.");
                resp.sendRedirect(req.getContextPath() + "/officer/scheduling?driveId=" + preview.driveId());
                return;
            }

            // Immediately mark consumed within synchronized block to guard against concurrent duplicate publication
            SchedulingPreviewState consumedState = preview.markConsumed();
            previewsMap.put(normPreviewId, consumedState);
            session.setAttribute(PREVIEW_SESSION_ATTR, consumedState);
        }

        if (preview.proposedSessions().isEmpty()) {
            session.setAttribute("flash_error", "Cannot publish an empty timetable with 0 scheduled appointments.");
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling?driveId=" + preview.driveId());
            return;
        }

        // Reconstruct domain InterviewSession objects exclusively from authoritative server preview
        List<InterviewSession> proposedSessions = new ArrayList<>();
        for (ProposedSessionDto dto : preview.proposedSessions()) {
            proposedSessions.add(new InterviewSession(
                    dto.sessionId(),
                    dto.applicationId(),
                    dto.interviewerId(),
                    dto.roomId(),
                    dto.startTime(),
                    dto.endTime()
            ));
        }

        // Execute publication using existing InterviewService transactional publication logic
        InterviewService service = resolveInterviewService();
        InterviewService.PublicationResult pubResult;
        try {
            pubResult = service.publishSchedule(proposedSessions, Duration.ofMinutes(preview.bufferMinutes()));
        } catch (Exception ex) {
            LOGGER.log(java.util.logging.Level.SEVERE, "[OfficerScheduling] Infrastructure failure publishing schedule " + normPreviewId, ex);
            PublicationSummaryState errorSummary = new PublicationSummaryState(
                    normPreviewId,
                    preview.driveId(),
                    preview.jobTitle(),
                    preview.companyName(),
                    Instant.now(),
                    0,
                    0,
                    0,
                    1,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("Infrastructure error during publication: " + ex.getMessage())
            );
            session.setAttribute(PUBLICATION_RESULT_SESSION_ATTR, errorSummary);
            resp.sendRedirect(req.getContextPath() + "/officer/scheduling/published?driveId=" + preview.driveId());
            return;
        }

        // Build comprehensive publication summary state
        Map<String, ProposedSessionDto> proposedBySessionId = preview.proposedSessions().stream()
                .collect(Collectors.toMap(ProposedSessionDto::sessionId, Function.identity(), (a, b) -> a));

        List<PublicationSummaryState.PublishedSessionDetail> publishedDetails = new ArrayList<>();
        for (InterviewSession ps : pubResult.publishedSessions()) {
            ProposedSessionDto dto = proposedBySessionId.get(ps.getId());
            String sId = dto != null ? dto.studentId() : "N/A";
            String sName = dto != null ? dto.studentName() : "N/A";
            String iName = dto != null ? dto.interviewerName() : ps.getInterviewerId();
            String rName = dto != null ? dto.roomName() : ps.getRoomId();
            publishedDetails.add(new PublicationSummaryState.PublishedSessionDetail(
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

        List<PublicationSummaryState.ConflictDetail> conflictDetails = new ArrayList<>();
        List<String> infrastructureErrors = new ArrayList<>();

        for (String rejectionMsg : pubResult.conflictRejections()) {
            if (rejectionMsg.startsWith("Failed to publish appointment")) {
                infrastructureErrors.add(rejectionMsg);
            } else {
                PublicationSummaryState.ConflictCategory cat = categorizeConflict(rejectionMsg);
                String appId = extractAppIdFromMessage(rejectionMsg);
                conflictDetails.add(new PublicationSummaryState.ConflictDetail(appId, rejectionMsg, cat));
            }
        }

        PublicationSummaryState summary = new PublicationSummaryState(
                normPreviewId,
                preview.driveId(),
                preview.jobTitle(),
                preview.companyName(),
                Instant.now(),
                pubResult.publishedCount(),
                pubResult.skippedDuplicateApplicationIds().size(),
                conflictDetails.size(),
                infrastructureErrors.size(),
                publishedDetails,
                pubResult.skippedDuplicateApplicationIds(),
                conflictDetails,
                infrastructureErrors
        );

        session.setAttribute(PUBLICATION_RESULT_SESSION_ATTR, summary);

        LOGGER.info(String.format("[OfficerScheduling] Published timetable %s: %d confirmed, %d skipped duplicates, %d conflicts, %d infrastructure errors",
                normPreviewId, summary.publishedCount(), summary.skippedDuplicatesCount(), summary.conflictsCount(), summary.errorsCount()));

        resp.sendRedirect(req.getContextPath() + "/officer/scheduling/published?driveId=" + preview.driveId());
    }

    private void renderPublishedTimetable(HttpServletRequest req, HttpServletResponse resp,
                                         UserPrincipal officer, HttpSession session)
            throws ServletException, IOException {

        List<RecruitmentDrive> drives = (driveRepository != null) ? driveRepository.findAll() : List.of();
        List<InterviewSession> allConfirmedSessions = (sessionRepository != null) ? sessionRepository.findAll() : List.of();

        String driveFilter = req.getParameter("driveId");
        String dateFilter = req.getParameter("date");

        if (driveFilter != null) driveFilter = driveFilter.trim().toUpperCase();
        if (dateFilter != null) dateFilter = dateFilter.trim();

        // Batch lookups to prevent unbounded N+1 queries
        Map<String, Application> appMap = (applicationRepository != null)
                ? applicationRepository.findAll().stream().collect(Collectors.toMap(Application::getId, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, Student> studentMap = (studentRepository != null)
                ? studentRepository.findAll().stream().collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, Interviewer> interviewerMap = (interviewerRepository != null)
                ? interviewerRepository.findAll().stream().collect(Collectors.toMap(Interviewer::getId, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, Room> roomMap = (roomRepository != null)
                ? roomRepository.findAll().stream().collect(Collectors.toMap(Room::getId, Function.identity(), (a, b) -> a))
                : Map.of();
        Map<String, RecruitmentDrive> driveMap = drives.stream()
                .collect(Collectors.toMap(RecruitmentDrive::getId, Function.identity(), (a, b) -> a));
        Map<String, Company> companyMap = (companyRepository != null)
                ? companyRepository.findAll().stream().collect(Collectors.toMap(Company::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        List<ConfirmedInterviewViewItem> items = new ArrayList<>();
        for (InterviewSession sess : allConfirmedSessions) {
            Application app = appMap.get(sess.getApplicationId());
            String dId = (app != null) ? app.getDriveId() : "N/A";

            if (driveFilter != null && !driveFilter.isEmpty() && !dId.equalsIgnoreCase(driveFilter)) {
                continue;
            }

            if (dateFilter != null && !dateFilter.isEmpty() && !sess.getStartTime().toLocalDate().toString().equals(dateFilter)) {
                continue;
            }

            RecruitmentDrive drive = driveMap.get(dId);
            String jobTitle = (drive != null) ? drive.getJobTitle() : "Drive " + dId;
            String compId = (drive != null) ? drive.getCompanyId() : "N/A";
            Company comp = companyMap.get(compId);
            String compName = (comp != null) ? comp.getName() : compId;

            String studentId = (app != null) ? app.getStudentId() : "N/A";
            Student student = studentMap.get(studentId);
            String studentName = (student != null) ? student.getName() : studentId;
            String studentBranch = (student != null) ? student.getBranch() : "N/A";

            Interviewer interviewer = interviewerMap.get(sess.getInterviewerId());
            String interviewerName = (interviewer != null) ? interviewer.getName() : sess.getInterviewerId();

            Room room = roomMap.get(sess.getRoomId());
            String roomName = (room != null) ? room.getName() : sess.getRoomId();

            ApplicationStatus status = (app != null) ? app.getStatus() : ApplicationStatus.SCHEDULED;

            items.add(new ConfirmedInterviewViewItem(
                    sess.getId(),
                    sess.getApplicationId(),
                    studentId,
                    studentName,
                    studentBranch,
                    dId,
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

        // Flash publication result (one-time display after PRG)
        Object flashResult = session.getAttribute(PUBLICATION_RESULT_SESSION_ATTR);
        if (flashResult != null) {
            req.setAttribute("publicationSummary", flashResult);
            session.removeAttribute(PUBLICATION_RESULT_SESSION_ATTR);
        }

        req.setAttribute("confirmedInterviews", items);
        req.setAttribute("drives", drives);
        req.setAttribute("selectedDriveId", driveFilter);
        req.setAttribute("selectedDate", dateFilter);
        req.setAttribute("user", officer);
        req.setAttribute("pageTitle", "Confirmed Interview Timetable - HireFlow");
        req.setAttribute("activeNav", "officer-timetable");

        req.getRequestDispatcher(PUBLISHED_VIEW).forward(req, resp);
    }

    private PublicationSummaryState.ConflictCategory categorizeConflict(String msg) {
        if (msg == null) return PublicationSummaryState.ConflictCategory.OTHER_CONFLICT;
        String lower = msg.toLowerCase();
        if (lower.contains("shortlisted")) {
            return PublicationSummaryState.ConflictCategory.STATUS_NOT_SHORTLISTED;
        } else if (lower.contains("student conflict")) {
            return PublicationSummaryState.ConflictCategory.STUDENT_OVERLAP;
        } else if (lower.contains("interviewer conflict")) {
            return PublicationSummaryState.ConflictCategory.INTERVIEWER_OVERLAP;
        } else if (lower.contains("room conflict")) {
            return PublicationSummaryState.ConflictCategory.ROOM_OVERLAP;
        } else if (lower.contains("already assigned")) {
            return PublicationSummaryState.ConflictCategory.SESSION_EXISTS;
        } else if (lower.contains("not exist")) {
            return PublicationSummaryState.ConflictCategory.RESOURCE_NOT_FOUND;
        }
        return PublicationSummaryState.ConflictCategory.OTHER_CONFLICT;
    }

    private String extractAppIdFromMessage(String msg) {
        if (msg == null) return "N/A";
        int start = msg.indexOf("'");
        if (start >= 0) {
            int end = msg.indexOf("'", start + 1);
            if (end > start) {
                return msg.substring(start + 1, end);
            }
        }
        return "N/A";
    }
}
