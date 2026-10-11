<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My Applications - HireFlow" />
<c:set var="breadcrumbCurrent" value="Applications Tracker" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1100px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Banner Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 1.75rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
            <div>
                <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem;">
                    <span class="badge badge-role"><c:out value="${student.branch}" /></span>
                    <span class="badge badge-cutoff">ID: <c:out value="${student.id}" /></span>
                    <span class="badge badge-active"><c:out value="${applicationItems.size()}" /> Applications</span>
                </div>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                    My Placement Applications Tracker
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    Live tracking of shortlisting, technical evaluation, and interview scheduling progression.
                </p>
            </div>
            <div>
                <a href="${pageContext.request.contextPath}/student/drives" class="btn btn-primary" id="exploreMoreDrivesBtn">
                    Browse More Drives &rarr;
                </a>
            </div>
        </div>
    </div>

    <!-- Applications Content -->
    <c:choose>
        <c:when test="${empty applicationItems}">
            <div class="card empty-state" style="padding: 3.5rem; text-align: center;">
                <div style="font-size: 3.5rem; margin-bottom: 1rem;">📝</div>
                <h3 style="font-size: 1.35rem; font-weight: 750; color: var(--text-primary); margin-bottom: 0.5rem;">
                    No Applications Submitted Yet
                </h3>
                <p style="color: var(--text-secondary); max-width: 480px; margin: 0 auto 1.5rem; line-height: 1.6;">
                    You have not submitted applications to any campus placement drives. Browse eligible drives matching your academic criteria to apply.
                </p>
                <a href="${pageContext.request.contextPath}/student/drives" class="btn btn-primary">
                    Find Eligible Recruitment Drives
                </a>
            </div>
        </c:when>
        <c:otherwise>
            <div class="card" style="overflow: hidden;">
                <div class="card-header" style="padding: 1.25rem 1.75rem; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center;">
                    <h2 style="font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin: 0;">
                        Submitted Candidate Applications
                    </h2>
                    <span style="font-size: 0.85rem; color: var(--text-muted);">
                        Showing <c:out value="${applicationItems.size()}"/> record(s)
                    </span>
                </div>
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse; font-size: 0.875rem; text-align: left;" id="applicationsTable">
                        <thead>
                            <tr style="background: var(--bg-secondary); border-bottom: 1px solid var(--border); color: var(--text-muted); font-size: 0.75rem; text-transform: uppercase;">
                                <th style="padding: 1rem 1.5rem;">App Reference</th>
                                <th style="padding: 1rem 1.5rem;">Corporate Partner</th>
                                <th style="padding: 1rem 1.5rem;">Job Role / Drive</th>
                                <th style="padding: 1rem 1.5rem;">Submitted Date</th>
                                <th style="padding: 1rem 1.5rem;">Lifecycle Status</th>
                                <th style="padding: 1rem 1.5rem;">Pipeline Status Details</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="item" items="${applicationItems}">
                                <tr style="border-bottom: 1px solid var(--border);" id="app-row-${item.application().id}">
                                    <td style="padding: 1rem 1.5rem; font-family: monospace; font-weight: 700; color: var(--primary);">
                                        <c:out value="${item.application().id}" />
                                    </td>
                                    <td style="padding: 1rem 1.5rem; font-weight: 600; color: var(--text-primary);">
                                        <c:out value="${item.companyName()}" />
                                    </td>
                                    <td style="padding: 1rem 1.5rem;">
                                        <div style="font-weight: 600; color: var(--text-primary);">
                                            <c:out value="${item.jobTitle()}" />
                                        </div>
                                        <div style="font-size: 0.75rem; color: var(--text-muted); font-family: monospace;">
                                            <c:out value="${item.application().driveId}" />
                                        </div>
                                    </td>
                                    <td style="padding: 1rem 1.5rem; color: var(--text-secondary); white-space: nowrap;">
                                        <c:out value="${item.formattedAppliedAt()}" />
                                    </td>
                                    <td style="padding: 1rem 1.5rem; white-space: nowrap;">
                                        <span class="badge ${item.statusBadgeClass()}" style="font-weight: 600;">
                                            <c:out value="${item.application().status}" />
                                        </span>
                                    </td>
                                    <td style="padding: 1rem 1.5rem; color: var(--text-secondary); font-size: 0.8rem; line-height: 1.4;">
                                        <c:out value="${item.statusExplanation()}" />
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </div>
            </div>
        </c:otherwise>
    </c:choose>

    <!-- Confidentiality & Placement Cell Notice -->
    <div class="card" style="margin-top: 2rem; border-left: 4px solid var(--primary);">
        <div class="card-body" style="padding: 1.25rem 1.75rem;">
            <h4 style="font-size: 0.95rem; font-weight: 700; color: var(--text-primary); margin: 0 0 0.25rem;">
                Interview Scheduling Protocol
            </h4>
            <p style="color: var(--text-secondary); font-size: 0.85rem; line-height: 1.5; margin: 0;">
                Once an application is moved to <code>SCHEDULED</code> status, your confirmed interview timetable and room allocation
                will appear in the candidate notification system. Confidential interviewer evaluations and recruiter rosters are restricted to placement officers.
            </p>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
