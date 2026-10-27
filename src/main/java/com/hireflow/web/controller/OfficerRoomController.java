package com.hireflow.web.controller;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.InterviewSession;
import com.hireflow.model.Room;
import com.hireflow.repository.InterviewSessionRepository;
import com.hireflow.repository.RoomRepository;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller providing placement officer interview venue/room management:
 * <ul>
 *     <li>GET  /officer/rooms          - List interview rooms with capacity</li>
 *     <li>GET  /officer/rooms/new      - Render room creation form</li>
 *     <li>POST /officer/rooms          - Create interview room</li>
 *     <li>GET  /officer/rooms/{id}/edit - Render edit form</li>
 *     <li>POST /officer/rooms/{id}/update - Update room name and capacity</li>
 *     <li>POST /officer/rooms/{id}/delete - Delete room (enforces referential integrity against confirmed sessions)</li>
 * </ul>
 */
public class OfficerRoomController extends HttpServlet {

    private static final String LIST_VIEW = "/WEB-INF/views/officer/rooms.jsp";
    private static final String FORM_VIEW = "/WEB-INF/views/officer/room-form.jsp";

    private static final Pattern EDIT_PATTERN = Pattern.compile("^/([^/]+)/edit/?$");
    private static final Pattern UPDATE_PATTERN = Pattern.compile("^/([^/]+)/update/?$");
    private static final Pattern DELETE_PATTERN = Pattern.compile("^/([^/]+)/delete/?$");

    public record RoomViewItem(
            Room room,
            int confirmedSessionCount
    ) {
        public Room getRoom() { return room; }
        public int getConfirmedSessionCount() { return confirmedSessionCount; }
    }

    private RoomRepository roomRepository;
    private InterviewSessionRepository sessionRepository;

    public OfficerRoomController() {
    }

    public OfficerRoomController(RoomRepository roomRepository,
                                 InterviewSessionRepository sessionRepository) {
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
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
                if (this.roomRepository == null) {
                    this.roomRepository = (RoomRepository) sc.getAttribute(WebContextListener.ROOM_REPO_ATTR);
                }
                if (this.sessionRepository == null) {
                    this.sessionRepository = (InterviewSessionRepository) sc.getAttribute(WebContextListener.SESSION_REPO_ATTR);
                }
            }
        }
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

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal officer = verifyOfficer(req, resp);
        if (officer == null) return;

        resolveDependencies();
        String pathInfo = req.getPathInfo();
        String csrfToken = CsrfTokenManager.getOrCreateToken(req.getSession(true));
        req.setAttribute("csrfToken", csrfToken);
        req.setAttribute("user", officer);

        // 1. Route: /officer/rooms/new
        if ("/new".equalsIgnoreCase(pathInfo)) {
            req.setAttribute("isEdit", false);
            req.setAttribute("pageTitle", "Add Interview Room - HireFlow");
            req.setAttribute("activeNav", "officer-rooms");
            req.getRequestDispatcher(FORM_VIEW).forward(req, resp);
            return;
        }

        // 2. Route: /officer/rooms/{id}/edit
        if (pathInfo != null) {
            Matcher editMatcher = EDIT_PATTERN.matcher(pathInfo);
            if (editMatcher.matches()) {
                String id = editMatcher.group(1);
                Optional<Room> opt = roomRepository.findById(id);
                if (opt.isEmpty()) {
                    req.getSession().setAttribute("flash_error", "Room '" + id + "' not found.");
                    resp.sendRedirect(req.getContextPath() + "/officer/rooms");
                    return;
                }
                req.setAttribute("room", opt.get());
                req.setAttribute("isEdit", true);
                req.setAttribute("pageTitle", "Edit Room " + id + " - HireFlow");
                req.setAttribute("activeNav", "officer-rooms");
                req.getRequestDispatcher(FORM_VIEW).forward(req, resp);
                return;
            }
        }

        // 3. Route: /officer/rooms (List)
        List<Room> rooms = roomRepository != null ? roomRepository.findAll() : List.of();
        List<RoomViewItem> items = new ArrayList<>();
        for (Room r : rooms) {
            int sessionCount = (sessionRepository != null) ? sessionRepository.findByRoomId(r.getId()).size() : 0;
            items.add(new RoomViewItem(r, sessionCount));
        }

        items.sort(Comparator.comparing((RoomViewItem it) -> it.room().getName()));

        req.setAttribute("items", items);
        req.setAttribute("pageTitle", "Interview Venues & Rooms - HireFlow");
        req.setAttribute("activeNav", "officer-rooms");
        req.getRequestDispatcher(LIST_VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal officer = verifyOfficer(req, resp);
        if (officer == null) return;

        // CSRF verification
        if (!CsrfTokenManager.validateToken(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token.");
            return;
        }

        resolveDependencies();
        HttpSession session = req.getSession();
        String pathInfo = req.getPathInfo();
        String action = req.getParameter("action");

        // 1. Delete action
        String deleteId = null;
        if (pathInfo != null) {
            Matcher delMatcher = DELETE_PATTERN.matcher(pathInfo);
            if (delMatcher.matches()) {
                deleteId = delMatcher.group(1);
            }
        }
        if (deleteId == null && "delete".equalsIgnoreCase(action)) {
            deleteId = req.getParameter("id");
        }

        if (deleteId != null) {
            String normDelId = deleteId.trim().toUpperCase();
            try {
                if (sessionRepository != null) {
                    List<InterviewSession> confirmed = sessionRepository.findByRoomId(normDelId);
                    if (!confirmed.isEmpty()) {
                        throw new ValidationException(String.format(
                                "Cannot delete room '%s' because %d confirmed interview session(s) reference this room.",
                                normDelId, confirmed.size()
                        ));
                    }
                }
                boolean deleted = roomRepository.deleteById(normDelId);
                if (deleted) {
                    session.setAttribute("flash_success", "Room '" + normDelId + "' deleted successfully.");
                } else {
                    session.setAttribute("flash_error", "Room '" + normDelId + "' not found.");
                }
            } catch (Exception ex) {
                session.setAttribute("flash_error", ex.getMessage());
            }
            resp.sendRedirect(req.getContextPath() + "/officer/rooms");
            return;
        }

        // 2. Update action
        String updateId = null;
        if (pathInfo != null) {
            Matcher updateMatcher = UPDATE_PATTERN.matcher(pathInfo);
            if (updateMatcher.matches()) {
                updateId = updateMatcher.group(1);
            }
        }
        if (updateId == null && "update".equalsIgnoreCase(action)) {
            updateId = req.getParameter("id");
        }

        if (updateId != null) {
            String normUpdateId = updateId.trim().toUpperCase();
            String name = req.getParameter("name");
            String capacityStr = req.getParameter("capacity");

            try {
                Optional<Room> opt = roomRepository.findById(normUpdateId);
                if (opt.isEmpty()) {
                    throw new EntityNotFoundException("Room '" + normUpdateId + "' not found.");
                }

                int capacity;
                try {
                    capacity = Integer.parseInt(capacityStr != null ? capacityStr.trim() : "0");
                } catch (NumberFormatException nfe) {
                    throw new ValidationException("Capacity must be a positive integer.");
                }

                Room room = opt.get();
                room.setName(name);
                room.setCapacity(capacity);
                roomRepository.update(room);

                session.setAttribute("flash_success", "Room '" + normUpdateId + "' updated successfully.");
                resp.sendRedirect(req.getContextPath() + "/officer/rooms");
            } catch (Exception ex) {
                session.setAttribute("flash_error", ex.getMessage());
                resp.sendRedirect(req.getContextPath() + "/officer/rooms/" + normUpdateId + "/edit");
            }
            return;
        }

        // 3. Create action
        String id = req.getParameter("id");
        String name = req.getParameter("name");
        String capacityStr = req.getParameter("capacity");

        try {
            if (id == null || id.trim().isEmpty()) {
                throw new ValidationException("Room ID is required.");
            }
            String normId = id.trim().toUpperCase();
            if (roomRepository.existsById(normId)) {
                throw new DuplicateEntityException("Room with ID '" + normId + "' already exists.");
            }

            int capacity;
            try {
                capacity = Integer.parseInt(capacityStr != null ? capacityStr.trim() : "0");
            } catch (NumberFormatException nfe) {
                throw new ValidationException("Capacity must be a positive integer.");
            }

            Room room = new Room(normId, name, capacity);
            roomRepository.add(room);

            session.setAttribute("flash_success", "Room '" + normId + "' created successfully.");
            resp.sendRedirect(req.getContextPath() + "/officer/rooms");
        } catch (Exception ex) {
            session.setAttribute("flash_error", ex.getMessage());
            resp.sendRedirect(req.getContextPath() + "/officer/rooms/new");
        }
    }
}
