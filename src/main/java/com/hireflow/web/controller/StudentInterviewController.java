package com.hireflow.web.controller;

import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.web.dto.ConfirmedInterviewViewItem;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.listener.WebContextListener;
import com.hireflow.web.security.UserPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller serving authenticated student candidate confirmed interview schedule (GET /student/interviews).
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Derives student ID strictly from authenticated {@link UserPrincipal}; never trusts client input.</li>
 *     <li>Queries confirmed PostgreSQL interview_sessions by student ID (or application ownership).</li>
 *     <li>Displays only the candidate's own confirmed appointments; never leaks other candidates' slots.</li>
 *     <li>Displays interview date, time window, company, role, room/venue, and interviewer name.</li>
 *     <li>Withholds private interviewer contact details, internal evaluation remarks, or unconfirmed proposals.</li>
 *     <li>Enforces strict anti-caching response headers.</li>
 * </ul>
 */
public class StudentInterviewController extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentInterviewController.class.getName());
    private static final String INTERVIEWS_VIEW = "/WEB-INF/views/student/interviews.jsp";

    private InterviewSessionRepository sessionRepository;
    private ApplicationRepository applicationRepository;
    private StudentRepository studentRepository;
    private DriveRepository driveRepository;
    private CompanyRepository companyRepository;
    private RoomRepository roomRepository;
    private InterviewerRepository interviewerRepository;

    public StudentInterviewController() {
    }

    public StudentInterviewController(InterviewSessionRepository sessionRepository,
                                      ApplicationRepository applicationRepository,
                                      StudentRepository studentRepository,
                                      DriveRepository driveRepository,
                                      CompanyRepository companyRepository,
                                      RoomRepository roomRepository,
                                      InterviewerRepository interviewerRepository) {
        this.sessionRepository = sessionRepository;
        this.applicationRepository = applicationRepository;
        this.studentRepository = studentRepository;
        this.driveRepository = driveRepository;
        this.companyRepository = companyRepository;
        this.roomRepository = roomRepository;
        this.interviewerRepository = interviewerRepository;
    }

    @Override
    public void init(jakarta.servlet.ServletConfig config) throws ServletException {
        super.init(config);
        resolveDependencies();
    }

    private void resolveDependencies() {
        var config = getServletConfig();
        if (config != null) {
            var sc = config.getServletContext();
            if (sc != null) {
                if (this.sessionRepository == null) {
                    this.sessionRepository = (InterviewSessionRepository) sc.getAttribute(WebContextListener.SESSION_REPO_ATTR);
                }
                if (this.applicationRepository == null) {
                    this.applicationRepository = (ApplicationRepository) sc.getAttribute(WebContextListener.APPLICATION_REPO_ATTR);
                }
                if (this.studentRepository == null) {
                    this.studentRepository = (StudentRepository) sc.getAttribute(WebContextListener.STUDENT_REPO_ATTR);
                }
                if (this.driveRepository == null) {
                    this.driveRepository = (DriveRepository) sc.getAttribute(WebContextListener.DRIVE_REPO_ATTR);
                }
                if (this.companyRepository == null) {
                    this.companyRepository = (CompanyRepository) sc.getAttribute(WebContextListener.COMPANY_REPO_ATTR);
                }
                if (this.roomRepository == null) {
                    this.roomRepository = (RoomRepository) sc.getAttribute(WebContextListener.ROOM_REPO_ATTR);
                }
                if (this.interviewerRepository == null) {
                    this.interviewerRepository = (InterviewerRepository) sc.getAttribute(WebContextListener.INTERVIEWER_REPO_ATTR);
                }
            }
        }
    }

    private void setAntiCacheHeaders(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);

        HttpSession session = req.getSession(false);
        UserPrincipal principal = (session != null)
                ? (UserPrincipal) session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)
                : null;

        if (principal == null || !principal.isStudent() || principal.getStudentId() == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Authenticated student account required.");
            return;
        }

        resolveDependencies();
        String studentId = principal.getStudentId().trim().toUpperCase();

        // 1. Authoritative candidate resolution
        Student student = (studentRepository != null)
                ? studentRepository.findById(studentId).orElse(null)
                : null;

        // 2. Load confirmed sessions exclusively belonging to this candidate
        List<InterviewSession> confirmedSessions = (sessionRepository != null)
                ? sessionRepository.findByStudentId(studentId)
                : List.of();

        // If repo findByStudentId returned empty, fall back to resolving candidate's applications
        if (confirmedSessions.isEmpty() && applicationRepository != null && sessionRepository != null) {
            List<Application> studentApps = applicationRepository.findByStudentId(studentId);
            confirmedSessions = studentApps.stream()
                    .flatMap(a -> sessionRepository.findByApplicationId(a.getId()).stream())
                    .sorted(Comparator.comparing(InterviewSession::getStartTime))
                    .toList();
        }

        // 3. Batch metadata lookups to prevent N+1 queries
        Map<String, Application> appMap = (applicationRepository != null)
                ? applicationRepository.findByStudentId(studentId).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        Map<String, RecruitmentDrive> driveMap = (driveRepository != null)
                ? driveRepository.findAll().stream().collect(Collectors.toMap(RecruitmentDrive::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        Map<String, Company> companyMap = (companyRepository != null)
                ? companyRepository.findAll().stream().collect(Collectors.toMap(Company::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        Map<String, Room> roomMap = (roomRepository != null)
                ? roomRepository.findAll().stream().collect(Collectors.toMap(Room::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        Map<String, Interviewer> interviewerMap = (interviewerRepository != null)
                ? interviewerRepository.findAll().stream().collect(Collectors.toMap(Interviewer::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        List<ConfirmedInterviewViewItem> interviewItems = new ArrayList<>();
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
            ApplicationStatus status = (app != null) ? app.getStatus() : ApplicationStatus.SCHEDULED;

            interviewItems.add(new ConfirmedInterviewViewItem(
                    sess.getId(),
                    sess.getApplicationId(),
                    studentId,
                    student != null ? student.getName() : principal.getDisplayName(),
                    student != null ? student.getBranch() : "N/A",
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

        req.setAttribute("interviews", interviewItems);
        req.setAttribute("student", student);
        req.setAttribute("user", principal);
        req.setAttribute("pageTitle", "My Confirmed Interviews - HireFlow");
        req.setAttribute("activeNav", "student-interviews");

        LOGGER.info(String.format("[StudentInterview] Rendered %d confirmed interview appointments for candidate '%s'",
                interviewItems.size(), studentId));

        req.getRequestDispatcher(INTERVIEWS_VIEW).forward(req, resp);
    }
}
