<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Interview Scheduling - HireFlow" />
<c:set var="activeNav" value="officer-scheduling" />
<c:set var="breadcrumbCurrent" value="Interview Scheduling" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1100px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Page Header -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem; flex-wrap: wrap; gap: 1rem;">
        <div>
            <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;">
                <a href="${pageContext.request.contextPath}/officer/dashboard" style="color: var(--text-muted); text-decoration: none; font-size: 0.85rem;">
                    &larr; Officer Console
                </a>
                <span style="color: var(--text-muted); font-size: 0.85rem;">/</span>
                <span style="font-size: 0.85rem; font-weight: 700; color: var(--primary); text-transform: uppercase;">
                    Automated Timetable Engine
                </span>
            </div>
            <h1 style="font-size: 2rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                Interview Scheduling Setup
            </h1>
            <p style="color: var(--text-muted); margin: 0; font-size: 0.95rem;">
                Configure scheduling windows and resource pools to generate deterministic advisory timetable proposals.
            </p>
        </div>
        <div style="display: flex; gap: 0.75rem; align-items: center;">
            <a href="${pageContext.request.contextPath}/officer/interviewers" class="btn btn-outline" id="btnManageInterviewers">
                🧑‍💼 Interviewers
            </a>
            <a href="${pageContext.request.contextPath}/officer/rooms" class="btn btn-outline" id="btnManageRooms">
                🚪 Rooms
            </a>
        </div>
    </div>

    <!-- Active Advisory Preview Notice if cached in session -->
    <c:if test="${not empty sessionScope['hireflow.schedulingPreview']}">
        <div class="card" style="margin-bottom: 1.5rem; border-left: 4px solid var(--primary); background: rgba(37, 99, 235, 0.04);">
            <div class="card-body" style="padding: 1rem 1.5rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                <div>
                    <strong>Active Preview Generated:</strong> Preview reference <code><c:out value="${sessionScope['hireflow.schedulingPreview'].previewId}"/></code>
                    for drive <strong><c:out value="${sessionScope['hireflow.schedulingPreview'].jobTitle}"/></strong>
                    (<c:out value="${sessionScope['hireflow.schedulingPreview'].totalScheduled}"/> scheduled,
                    <c:out value="${sessionScope['hireflow.schedulingPreview'].totalUnscheduled}"/> unscheduled).
                </div>
                <a href="${pageContext.request.contextPath}/officer/scheduling/preview" class="btn btn-primary" style="font-size: 0.85rem;" id="btnViewCachedPreview">
                    View Existing Preview &rarr;
                </a>
            </div>
        </div>
    </c:if>

    <form action="${pageContext.request.contextPath}/officer/scheduling/preview" method="POST" id="schedulingConfigForm">
        <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />

        <!-- Step 1: Recruitment Drive Selection -->
        <div class="card" style="margin-bottom: 1.5rem;">
            <div class="card-body" style="padding: 1.75rem;">
                <h2 style="font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                    1. Select Recruitment Drive &amp; Candidate Pool
                </h2>
                <p style="color: var(--text-muted); font-size: 0.875rem; margin: 0 0 1.25rem;">
                    Only candidates currently in <strong>SHORTLISTED</strong> status are eligible for interview scheduling.
                </p>

                <div style="margin-bottom: 1rem;">
                    <label for="driveSelect" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                        Recruitment Drive <span style="color: var(--danger);">*</span>
                    </label>
                    <select id="driveSelect" name="driveId" required class="form-control"
                            onchange="window.location.href='${pageContext.request.contextPath}/officer/scheduling?driveId='+encodeURIComponent(this.value);"
                            style="width: 100%; padding: 0.7rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px; font-size: 0.95rem; background-color: var(--bg-surface); color: var(--text-primary);">
                        <option value="">-- Choose Recruitment Drive --</option>
                        <c:forEach var="drive" items="${drives}">
                            <c:set var="scCount" value="${shortlistedCounts[drive.id] != null ? shortlistedCounts[drive.id] : 0}"/>
                            <option value="<c:out value='${drive.id}'/>" <c:if test="${selectedDriveId == drive.id}">selected</c:if>>
                                <c:out value="${drive.id}"/> - <c:out value="${drive.jobTitle}"/> (<c:out value="${companyMap[drive.companyId] != null ? companyMap[drive.companyId].name : drive.companyId}"/>) — [<c:out value="${scCount}"/> Shortlisted]
                            </option>
                        </c:forEach>
                    </select>
                </div>

                <c:if test="${not empty selectedDriveId}">
                    <c:set var="curCount" value="${shortlistedCounts[selectedDriveId] != null ? shortlistedCounts[selectedDriveId] : 0}"/>
                    <div style="background: ${curCount > 0 ? 'rgba(34, 197, 94, 0.08)' : 'rgba(239, 68, 68, 0.08)'}; border: 1px solid ${curCount > 0 ? 'rgba(34, 197, 94, 0.3)' : 'rgba(239, 68, 68, 0.3)'}; border-radius: 6px; padding: 0.85rem 1.25rem;">
                        <c:choose>
                            <c:when test="${curCount > 0}">
                                <span style="color: var(--success); font-weight: 600;">✓ Qualified Pool Available:</span>
                                <span style="color: var(--text-primary);">
                                    Found <strong><c:out value="${curCount}"/></strong> candidate(s) in <strong>SHORTLISTED</strong> status ready to be scheduled.
                                </span>
                            </c:when>
                            <c:otherwise>
                                <span style="color: var(--danger); font-weight: 600;">⚠️ No Shortlisted Candidates:</span>
                                <span style="color: var(--text-secondary);">
                                    This drive currently has <strong>0</strong> shortlisted candidates. Visit the
                                    <a href="${pageContext.request.contextPath}/officer/drives/${selectedDriveId}/applications" style="color: var(--primary); font-weight: 600;">
                                        Applicants Review Screen
                                    </a> to evaluate and shortlist applicants before generating a schedule.
                                </span>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:if>
            </div>
        </div>

        <!-- Step 2: Time Window & Duration Parameters -->
        <div class="card" style="margin-bottom: 1.5rem;">
            <div class="card-body" style="padding: 1.75rem;">
                <h2 style="font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                    2. Scheduling Time Window &amp; Duration Constraints
                </h2>
                <p style="color: var(--text-muted); font-size: 0.875rem; margin: 0 0 1.25rem;">
                    Define the interview date, operational hours, slot duration, and transition buffers.
                </p>

                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1.25rem;">
                    <div>
                        <label for="schedDate" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                            Interview Date <span style="color: var(--danger);">*</span>
                        </label>
                        <input type="date" id="schedDate" name="date" value="<c:out value='${defaultDate}'/>" required
                               class="form-control" style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px;" />
                    </div>

                    <div>
                        <label for="schedStartTime" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                            Window Start Time <span style="color: var(--danger);">*</span>
                        </label>
                        <input type="time" id="schedStartTime" name="startTime" value="<c:out value='${defaultStartTime}'/>" required
                               class="form-control" style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px;" />
                    </div>

                    <div>
                        <label for="schedEndTime" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                            Window End Time <span style="color: var(--danger);">*</span>
                        </label>
                        <input type="time" id="schedEndTime" name="endTime" value="<c:out value='${defaultEndTime}'/>" required
                               class="form-control" style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px;" />
                    </div>

                    <div>
                        <label for="schedDuration" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                            Interview Duration <span style="color: var(--danger);">*</span>
                        </label>
                        <select id="schedDuration" name="duration" required class="form-control"
                                style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px; background-color: var(--bg-surface); color: var(--text-primary);">
                            <option value="15">15 Minutes</option>
                            <option value="30">30 Minutes</option>
                            <option value="45" selected>45 Minutes</option>
                            <option value="60">60 Minutes (1 Hour)</option>
                            <option value="90">90 Minutes</option>
                        </select>
                    </div>

                    <div>
                        <label for="schedBuffer" style="display: block; font-size: 0.875rem; font-weight: 600; margin-bottom: 0.4rem; color: var(--text-primary);">
                            Slot Buffer / Gap <span style="color: var(--danger);">*</span>
                        </label>
                        <select id="schedBuffer" name="buffer" required class="form-control"
                                style="width: 100%; padding: 0.65rem 0.85rem; border: 1px solid var(--border-color); border-radius: 6px; background-color: var(--bg-surface); color: var(--text-primary);">
                            <option value="0">0 Minutes (Back-to-back)</option>
                            <option value="5">5 Minutes</option>
                            <option value="10">10 Minutes</option>
                            <option value="15" selected>15 Minutes Gap</option>
                            <option value="30">30 Minutes Gap</option>
                        </select>
                    </div>
                </div>
            </div>
        </div>

        <!-- Step 3: Available Resources Pool -->
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(350px, 1fr)); gap: 1.5rem; margin-bottom: 2rem;">
            <!-- Interviewers Pool -->
            <div class="card">
                <div class="card-body" style="padding: 1.5rem;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
                        <h3 style="font-size: 1.05rem; font-weight: 700; color: var(--text-primary); margin: 0;">
                            🧑‍💼 Available Interviewers
                        </h3>
                        <span style="font-size: 0.8rem; color: var(--text-muted);">
                            <c:out value="${interviewers.size()}"/> registered
                        </span>
                    </div>
                    <p style="color: var(--text-muted); font-size: 0.8rem; margin: 0 0 1rem;">
                        Check interviewers available for assignment. Greedy scheduler matches company affiliation automatically.
                    </p>

                    <div style="max-height: 220px; overflow-y: auto; border: 1px solid var(--border-color); border-radius: 6px; padding: 0.5rem;">
                        <c:choose>
                            <c:when test="${not empty interviewers}">
                                <c:forEach var="i" items="${interviewers}">
                                    <label style="display: flex; align-items: center; gap: 0.5rem; padding: 0.35rem 0.5rem; font-size: 0.85rem; border-radius: 4px; cursor: pointer; hover:background: var(--bg-secondary);">
                                        <input type="checkbox" name="interviewerIds" value="<c:out value='${i.id}'/>" checked />
                                        <span>
                                            <strong><c:out value="${i.name}"/></strong>
                                            <span style="color: var(--text-muted); font-size: 0.75rem;">
                                                (<c:out value="${companyMap[i.companyId] != null ? companyMap[i.companyId].name : i.companyId}"/>)
                                            </span>
                                        </span>
                                    </label>
                                </c:forEach>
                            </c:when>
                            <c:otherwise>
                                <div style="color: var(--danger); font-size: 0.85rem; padding: 1rem; text-align: center;">
                                    No interviewers registered. Please add interviewers first.
                                </div>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </div>

            <!-- Interview Rooms Pool -->
            <div class="card">
                <div class="card-body" style="padding: 1.5rem;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
                        <h3 style="font-size: 1.05rem; font-weight: 700; color: var(--text-primary); margin: 0;">
                            🚪 Available Rooms &amp; Venues
                        </h3>
                        <span style="font-size: 0.8rem; color: var(--text-muted);">
                            <c:out value="${rooms.size()}"/> configured
                        </span>
                    </div>
                    <p style="color: var(--text-muted); font-size: 0.8rem; margin: 0 0 1rem;">
                        Check venues available for hosting evaluation sessions during the window.
                    </p>

                    <div style="max-height: 220px; overflow-y: auto; border: 1px solid var(--border-color); border-radius: 6px; padding: 0.5rem;">
                        <c:choose>
                            <c:when test="${not empty rooms}">
                                <c:forEach var="r" items="${rooms}">
                                    <label style="display: flex; align-items: center; gap: 0.5rem; padding: 0.35rem 0.5rem; font-size: 0.85rem; border-radius: 4px; cursor: pointer;">
                                        <input type="checkbox" name="roomIds" value="<c:out value='${r.id}'/>" checked />
                                        <span>
                                            <strong><c:out value="${r.name}"/></strong>
                                            <span style="color: var(--text-muted); font-size: 0.75rem;">
                                                [ID: <c:out value="${r.id}"/>, Cap: <c:out value="${r.capacity}"/>]
                                            </span>
                                        </span>
                                    </label>
                                </c:forEach>
                            </c:when>
                            <c:otherwise>
                                <div style="color: var(--danger); font-size: 0.85rem; padding: 1rem; text-align: center;">
                                    No rooms configured. Please add interview rooms first.
                                </div>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </div>
        </div>

        <!-- Submit & Preview Button Box -->
        <div class="card">
            <div class="card-body" style="padding: 1.5rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                <div style="color: var(--text-muted); font-size: 0.85rem;">
                    ℹ️ Generating a preview runs the GreedyScheduler in-memory without altering database records or changing candidate statuses.
                </div>
                <div style="display: flex; gap: 1rem; align-items: center;">
                    <a href="${pageContext.request.contextPath}/officer/dashboard" class="btn btn-outline" id="btnCancelConfig">
                        Cancel
                    </a>
                    <button type="submit" class="btn btn-primary" id="btnGeneratePreview"
                            <c:if test="${empty selectedDriveId || shortlistedCounts[selectedDriveId] == null || shortlistedCounts[selectedDriveId] == 0}">disabled title="Shortlist candidates before scheduling"</c:if>>
                        ⚡ Generate Proposed Timetable Preview
                    </button>
                </div>
            </div>
        </div>
    </form>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
