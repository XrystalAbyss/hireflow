<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Officer Console - HireFlow" />
<c:set var="activeNav" value="officer" />
<c:set var="breadcrumbCurrent" value="TPC Administration Console" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1200px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Welcome Header Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 2rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
            <div>
                <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem;">
                    <span class="badge badge-active"><c:out value="${user.role.displayName}" /></span>
                    <span class="badge badge-role">ADMIN CONSOLE</span>
                    <span class="badge badge-outline" style="border: 1px solid var(--border-color); color: var(--text-muted); font-size: 0.75rem;">Placement Officer Portal</span>
                </div>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                    Welcome, <c:out value="${user.displayName}" />
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    Username: <code><c:out value="${user.name}" /></code> | Training &amp; Placement Cell Recruitment Operations
                </p>
            </div>
            <div style="display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap;">
                <a href="${pageContext.request.contextPath}/officer/scheduling" class="btn btn-primary" id="btnQuickScheduling">
                    🗓️ Schedule Interviews
                </a>
                <a href="${pageContext.request.contextPath}/officer/drives/new" class="btn btn-outline" id="btnQuickNewDrive">
                    + Publish Drive
                </a>
                <a href="${pageContext.request.contextPath}/officer/companies/new" class="btn btn-outline" id="btnQuickNewCompany">
                    + Add Company
                </a>
                <form action="${pageContext.request.contextPath}/logout" method="POST" id="officerLogoutForm" style="display: inline;">
                    <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />
                    <button type="submit" class="btn btn-outline" id="officerLogoutBtn" style="color: var(--danger); border-color: rgba(239, 68, 68, 0.4);">
                        Sign Out
                    </button>
                </form>
            </div>
        </div>
    </div>

    <!-- Live Placement Metrics Grid -->
    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1.25rem; margin-bottom: 2rem;">
        <div class="card metric-card">
            <div class="card-body" style="padding: 1.5rem; text-align: center;">
                <div style="font-size: 0.85rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">
                    Registered Companies
                </div>
                <div style="font-size: 2.25rem; font-weight: 800; color: var(--primary);" id="metricCompanies">
                    <c:out value="${metrics.totalCompanies}" />
                </div>
                <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.25rem;">
                    <a href="${pageContext.request.contextPath}/officer/companies" style="color: var(--primary); text-decoration: none;">Manage Partners &rarr;</a>
                </div>
            </div>
        </div>

        <div class="card metric-card">
            <div class="card-body" style="padding: 1.5rem; text-align: center;">
                <div style="font-size: 0.85rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">
                    Recruitment Drives
                </div>
                <div style="font-size: 2.25rem; font-weight: 800; color: var(--text-primary);" id="metricDrives">
                    <c:out value="${metrics.totalDrives}" />
                </div>
                <div style="font-size: 0.75rem; color: var(--success); font-weight: 600; margin-top: 0.25rem;">
                    <c:out value="${metrics.activeDrives}" /> Active Windows
                </div>
            </div>
        </div>

        <div class="card metric-card">
            <div class="card-body" style="padding: 1.5rem; text-align: center;">
                <div style="font-size: 0.85rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">
                    Total Applications
                </div>
                <div style="font-size: 2.25rem; font-weight: 800; color: var(--text-primary);" id="metricApplications">
                    <c:out value="${metrics.totalApplications}" />
                </div>
                <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.25rem;">
                    <a href="${pageContext.request.contextPath}/officer/applications" style="color: var(--primary); text-decoration: none;">Review Pipeline &rarr;</a>
                </div>
            </div>
        </div>

        <div class="card metric-card">
            <div class="card-body" style="padding: 1.5rem; text-align: center;">
                <div style="font-size: 0.85rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">
                    Shortlisted Candidates
                </div>
                <div style="font-size: 2.25rem; font-weight: 800; color: #8b5cf6;" id="metricShortlisted">
                    <c:out value="${metrics.shortlistedApplications}" />
                </div>
                <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.25rem;">
                    Eligible for Interviews
                </div>
            </div>
        </div>

        <div class="card metric-card">
            <div class="card-body" style="padding: 1.5rem; text-align: center;">
                <div style="font-size: 0.85rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 0.5rem;">
                    Scheduled Slots
                </div>
                <div style="font-size: 2.25rem; font-weight: 800; color: var(--success);" id="metricScheduled">
                    <c:out value="${metrics.scheduledInterviews}" />
                </div>
                <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.25rem;">
                    <a href="${pageContext.request.contextPath}/officer/scheduling" style="color: var(--primary); text-decoration: none;">Launch Scheduling &rarr;</a>
                </div>
            </div>
        </div>
    </div>

    <!-- Active Drives Table -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 1.75rem;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem;">
                <div>
                    <h2 style="font-size: 1.25rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.25rem;">
                        🏢 Active Recruitment Drives
                    </h2>
                    <p style="color: var(--text-muted); font-size: 0.875rem; margin: 0;">
                        Campus recruitment windows currently accepting candidate registrations.
                    </p>
                </div>
                <a href="${pageContext.request.contextPath}/officer/drives" class="btn btn-outline" style="font-size: 0.85rem;">
                    View All Drives &rarr;
                </a>
            </div>

            <c:choose>
                <c:when test="${not empty metrics.upcomingDrives}">
                    <div style="overflow-x: auto;">
                        <table style="width: 100%; border-collapse: collapse; font-size: 0.9rem;">
                            <thead>
                                <tr style="border-bottom: 2px solid var(--border-color); text-align: left;">
                                    <th style="padding: 0.75rem 1rem;">Drive ID</th>
                                    <th style="padding: 0.75rem 1rem;">Company ID</th>
                                    <th style="padding: 0.75rem 1rem;">Job Title</th>
                                    <th style="padding: 0.75rem 1rem;">Min CGPA</th>
                                    <th style="padding: 0.75rem 1rem;">Eligible Branches</th>
                                    <th style="padding: 0.75rem 1rem;">Deadline</th>
                                    <th style="padding: 0.75rem 1rem; text-align: right;">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="drive" items="${metrics.upcomingDrives}">
                                    <tr style="border-bottom: 1px solid var(--border-color);">
                                        <td style="padding: 0.75rem 1rem; font-weight: 600;">
                                            <code><c:out value="${drive.id}"/></code>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:out value="${drive.companyId}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; font-weight: 600; color: var(--text-primary);">
                                            <c:out value="${drive.jobTitle}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:out value="${drive.minimumCgpa}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:forEach var="branch" items="${drive.eligibleBranches}">
                                                <span class="badge" style="background: rgba(37, 99, 235, 0.1); color: var(--primary); margin-right: 0.25rem; font-size: 0.75rem;">
                                                    <c:out value="${branch}"/>
                                                </span>
                                            </c:forEach>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; color: var(--text-muted);">
                                            <c:out value="${drive.applicationDeadline}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; text-align: right;">
                                            <a href="${pageContext.request.contextPath}/officer/drives/${drive.id}" class="btn btn-outline" style="font-size: 0.8rem; padding: 0.35rem 0.65rem;">
                                                Details
                                            </a>
                                            <a href="${pageContext.request.contextPath}/officer/applications?driveId=${drive.id}" class="btn btn-primary" style="font-size: 0.8rem; padding: 0.35rem 0.65rem;">
                                                Applicants
                                            </a>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:when>
                <c:otherwise>
                    <div style="text-align: center; padding: 2.5rem 1rem; color: var(--text-muted);">
                        <p style="font-size: 1rem; margin-bottom: 1rem;">No active recruitment drives at this time.</p>
                        <a href="${pageContext.request.contextPath}/officer/drives/new" class="btn btn-primary">
                            + Publish First Drive
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </div>

    <!-- Management Hub Quick Links -->
    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.5rem; margin-bottom: 2rem;">
        <div class="card">
            <div class="card-body" style="padding: 1.75rem;">
                <h3 style="font-size: 1.1rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                    🏢 Corporate Partners
                </h3>
                <p style="color: var(--text-secondary); font-size: 0.875rem; line-height: 1.6; margin-bottom: 1.25rem;">
                    Register new employer profiles, update corporate partner contacts, and verify visiting recruitment organizations.
                </p>
                <div style="display: flex; gap: 0.5rem;">
                    <a href="${pageContext.request.contextPath}/officer/companies" class="btn btn-primary" style="font-size: 0.85rem;">
                        View Directory &rarr;
                    </a>
                    <a href="${pageContext.request.contextPath}/officer/companies/new" class="btn btn-outline" style="font-size: 0.85rem;">
                        + New Company
                    </a>
                </div>
            </div>
        </div>

        <div class="card">
            <div class="card-body" style="padding: 1.75rem;">
                <h3 style="font-size: 1.1rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.5rem;">
                    📋 Applicant Reviews &amp; Shortlisting
                </h3>
                <p style="color: var(--text-secondary); font-size: 0.875rem; line-height: 1.6; margin-bottom: 1.25rem;">
                    Examine candidate profiles, re-evaluate branch and CGPA requirements, and promote qualified students to technical interview rounds.
                </p>
                <a href="${pageContext.request.contextPath}/officer/applications" class="btn btn-primary" style="font-size: 0.85rem;">
                    Review Applications &rarr;
                </a>
            </div>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
