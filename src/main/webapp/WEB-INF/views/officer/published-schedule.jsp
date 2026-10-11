<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Confirmed Interview Timetable - HireFlow" />
<c:set var="activeNav" value="officer-timetable" />
<c:set var="breadcrumbCurrent" value="Confirmed Timetable" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1200px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Breadcrumb / Header Actions -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; flex-wrap: wrap; gap: 1rem;">
        <div>
            <h1 style="font-size: 1.85rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                📅 Confirmed Interview Timetable
            </h1>
            <p style="color: var(--text-muted); font-size: 0.9rem; margin: 0;">
                Authoritative interview appointments persisted in PostgreSQL with transactional resource locks and GiST exclusion constraints.
            </p>
        </div>
        <div style="display: flex; gap: 0.75rem; align-items: center;">
            <a href="${pageContext.request.contextPath}/officer/scheduling" class="btn btn-outline" id="btnNewSchedule">
                ➕ New Schedule Simulation
            </a>
            <button type="button" class="btn btn-outline" onclick="window.print();" id="btnPrintTimetable">
                🖨️ Print Timetable
            </button>
        </div>
    </div>

    <!-- Flash Publication Results Banner (if redirected after publication) -->
    <c:if test="${not empty publicationSummary}">
        <div class="card" style="margin-bottom: 2rem; border-left: 6px solid ${publicationSummary.completelySuccessful ? '#10b981' : '#f59e0b'}; background: ${publicationSummary.completelySuccessful ? 'rgba(16, 185, 129, 0.05)' : 'rgba(245, 158, 11, 0.05)'};" id="publicationResultBanner">
            <div class="card-body" style="padding: 1.5rem 1.75rem;">
                <div style="display: flex; align-items: flex-start; gap: 1rem;">
                    <div style="font-size: 2rem;">
                        <c:choose>
                            <c:when test="${publicationSummary.completelySuccessful}">✅</c:when>
                            <c:otherwise>⚠️</c:otherwise>
                        </c:choose>
                    </div>
                    <div style="flex: 1;">
                        <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 0.5rem;">
                            <h2 style="font-size: 1.25rem; font-weight: 800; margin: 0; color: ${publicationSummary.completelySuccessful ? 'var(--success)' : '#b45309'};">
                                <c:choose>
                                    <c:when test="${publicationSummary.completelySuccessful}">
                                        Schedule Publication Complete — All Proposals Confirmed
                                    </c:when>
                                    <c:otherwise>
                                        Schedule Publication Completed with Partial Diagnostics
                                    </c:otherwise>
                                </c:choose>
                            </h2>
                            <span style="font-size: 0.75rem; color: var(--text-muted); font-family: monospace;">
                                Preview Token: <c:out value="${publicationSummary.previewId}"/>
                            </span>
                        </div>

                        <div style="display: flex; gap: 1.5rem; flex-wrap: wrap; margin-bottom: 0.75rem; font-size: 0.9rem;">
                            <span style="color: var(--success); font-weight: 700;">
                                ✔️ <strong><c:out value="${publicationSummary.publishedCount}"/></strong> Confirmed
                            </span>
                            <c:if test="${publicationSummary.skippedDuplicatesCount > 0}">
                                <span style="color: var(--primary); font-weight: 600;">
                                    ℹ️ <strong><c:out value="${publicationSummary.skippedDuplicatesCount}"/></strong> Skipped (Already Scheduled)
                                </span>
                            </c:if>
                            <c:if test="${publicationSummary.conflictsCount > 0}">
                                <span style="color: var(--danger); font-weight: 600;">
                                    ⚠️ <strong><c:out value="${publicationSummary.conflictsCount}"/></strong> Resource Conflicts
                                </span>
                            </c:if>
                            <c:if test="${publicationSummary.errorsCount > 0}">
                                <span style="color: var(--danger); font-weight: 700;">
                                    ❌ <strong><c:out value="${publicationSummary.errorsCount}"/></strong> Infrastructure Errors
                                </span>
                            </c:if>
                        </div>

                        <!-- Conflict Diagnostics Breakdown -->
                        <c:if test="${not empty publicationSummary.conflictDetails}">
                            <div style="margin-top: 1rem; padding: 0.75rem 1rem; background: rgba(239, 68, 68, 0.08); border-radius: 6px;">
                                <div style="font-size: 0.85rem; font-weight: 700; color: var(--danger); margin-bottom: 0.5rem;">
                                    Conflict Rejections &amp; Boundary Violations:
                                </div>
                                <ul style="margin: 0; padding-left: 1.25rem; font-size: 0.85rem; color: var(--text-secondary);">
                                    <c:forEach var="conflict" items="${publicationSummary.conflictDetails}">
                                        <li style="margin-bottom: 0.25rem;">
                                            <span class="badge" style="background: rgba(239, 68, 68, 0.2); color: var(--danger); font-size: 0.7rem;">
                                                <c:out value="${conflict.category}"/>
                                            </span>
                                            <code><c:out value="${conflict.applicationId}"/></code>: <c:out value="${conflict.reason}"/>
                                        </li>
                                    </c:forEach>
                                </ul>
                            </div>
                        </c:if>

                        <!-- Infrastructure Errors -->
                        <c:if test="${not empty publicationSummary.infrastructureErrors}">
                            <div style="margin-top: 0.75rem; padding: 0.75rem 1rem; background: rgba(239, 68, 68, 0.15); border-radius: 6px;">
                                <div style="font-size: 0.85rem; font-weight: 700; color: var(--danger); margin-bottom: 0.25rem;">
                                    Database / Concurrency Failure:
                                </div>
                                <c:forEach var="err" items="${publicationSummary.infrastructureErrors}">
                                    <div style="font-size: 0.85rem; color: var(--danger); font-family: monospace;">
                                        <c:out value="${err}"/>
                                    </div>
                                </c:forEach>
                            </div>
                        </c:if>
                    </div>
                </div>
            </div>
        </div>
    </c:if>

    <!-- Filter Control Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 1.25rem 1.5rem;">
            <form method="get" action="${pageContext.request.contextPath}/officer/scheduling/published" style="display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap;" id="formFilterTimetable">
                <div style="flex: 1; min-width: 250px;">
                    <label for="filterDrive" style="display: block; font-size: 0.85rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 0.35rem;">
                        Recruitment Drive Filter
                    </label>
                    <select name="driveId" id="filterDrive" class="form-control" style="width: 100%; padding: 0.5rem 0.75rem; border-radius: 6px; border: 1px solid var(--border-color); background: var(--bg-surface); color: var(--text-primary);">
                        <option value="">-- All Recruitment Drives --</option>
                        <c:forEach var="d" items="${drives}">
                            <option value="<c:out value='${d.id}'/>" ${selectedDriveId == d.id ? 'selected' : ''}>
                                <c:out value="${d.jobTitle}"/> (ID: <c:out value="${d.id}"/>)
                            </option>
                        </c:forEach>
                    </select>
                </div>

                <div style="width: 180px;">
                    <label for="filterDate" style="display: block; font-size: 0.85rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 0.35rem;">
                        Interview Date
                    </label>
                    <input type="date" name="date" id="filterDate" class="form-control" value="<c:out value='${selectedDate}'/>"
                           style="width: 100%; padding: 0.5rem 0.75rem; border-radius: 6px; border: 1px solid var(--border-color); background: var(--bg-surface); color: var(--text-primary);" />
                </div>

                <div style="display: flex; gap: 0.5rem;">
                    <button type="submit" class="btn btn-primary" id="btnApplyFilter">
                        🔍 Apply Filter
                    </button>
                    <a href="${pageContext.request.contextPath}/officer/scheduling/published" class="btn btn-outline" id="btnResetFilter">
                        Reset
                    </a>
                </div>
            </form>
        </div>
    </div>

    <!-- Confirmed Appointments Table Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 1.75rem;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
                <div>
                    <h2 style="font-size: 1.25rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.25rem;">
                        Confirmed Appointments
                    </h2>
                    <p style="color: var(--text-muted); font-size: 0.875rem; margin: 0;">
                        Showing persistent appointments matching the active filter criteria.
                    </p>
                </div>
                <span class="badge badge-eligible" id="countConfirmedBadge">
                    <c:out value="${confirmedInterviews.size()}"/> Confirmed Records
                </span>
            </div>

            <c:choose>
                <c:when test="${not empty confirmedInterviews}">
                    <div style="overflow-x: auto;">
                        <table style="width: 100%; border-collapse: collapse; font-size: 0.9rem;" id="tableConfirmedTimetable">
                            <thead>
                                <tr style="border-bottom: 2px solid var(--border-color); text-align: left;">
                                    <th style="padding: 0.75rem 1rem;">Date &amp; Time Slot</th>
                                    <th style="padding: 0.75rem 1rem;">Candidate / Student</th>
                                    <th style="padding: 0.75rem 1rem;">Recruitment Drive</th>
                                    <th style="padding: 0.75rem 1rem;">Interviewer Panel</th>
                                    <th style="padding: 0.75rem 1rem;">Room / Venue</th>
                                    <th style="padding: 0.75rem 1rem;">Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="item" items="${confirmedInterviews}">
                                    <tr style="border-bottom: 1px solid var(--border-color);" id="rowSession-${item.sessionId}">
                                        <td style="padding: 0.75rem 1rem; font-weight: 700; color: var(--text-primary); white-space: nowrap;">
                                            <div>🗓️ <c:out value="${item.date}"/></div>
                                            <div style="font-size: 0.85rem; color: var(--primary);">
                                                🕒 <c:out value="${item.startTimeOnly}"/> – <c:out value="${item.endTimeOnly}"/>
                                            </div>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <div style="font-weight: 600; color: var(--text-primary);">
                                                <c:out value="${item.studentName}"/>
                                            </div>
                                            <div style="font-size: 0.75rem; color: var(--text-muted);">
                                                <code><c:out value="${item.studentId}"/></code> • <c:out value="${item.studentBranch}"/>
                                            </div>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <div style="font-weight: 600; color: var(--text-primary);">
                                                <c:out value="${item.jobTitle}"/>
                                            </div>
                                            <div style="font-size: 0.75rem; color: var(--text-muted);">
                                                🏢 <c:out value="${item.companyName}"/> (<code><c:out value="${item.driveId}"/></code>)
                                            </div>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <div style="font-weight: 500; color: var(--text-secondary);">
                                                🧑‍💼 <c:out value="${item.interviewerName}"/>
                                            </div>
                                            <div style="font-size: 0.75rem; color: var(--text-muted); font-family: monospace;">
                                                <c:out value="${item.interviewerId}"/>
                                            </div>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <div style="font-weight: 500; color: var(--text-secondary);">
                                                🚪 <c:out value="${item.roomName}"/>
                                            </div>
                                            <div style="font-size: 0.75rem; color: var(--text-muted); font-family: monospace;">
                                                <c:out value="${item.roomId}"/>
                                            </div>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <span class="badge" style="background: rgba(16, 185, 129, 0.15); color: var(--success); font-weight: 700;">
                                                <c:out value="${item.status}"/>
                                            </span>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:when>
                <c:otherwise>
                    <div style="text-align: center; padding: 3rem 1rem; color: var(--text-muted);" id="emptyTimetableState">
                        <div style="font-size: 2.5rem; margin-bottom: 0.75rem;">📅</div>
                        <h3 style="font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin-bottom: 0.5rem;">
                            No Confirmed Interview Appointments Found
                        </h3>
                        <p style="font-size: 0.9rem; margin-bottom: 1.5rem; max-width: 500px; margin-left: auto; margin-right: auto;">
                            No confirmed appointments match the selected filter criteria. Generate and publish a proposed timetable to record confirmed sessions.
                        </p>
                        <a href="${pageContext.request.contextPath}/officer/scheduling" class="btn btn-primary">
                            🚀 Go to Automated Scheduling
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
