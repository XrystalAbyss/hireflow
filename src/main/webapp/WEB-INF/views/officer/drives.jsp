<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Recruitment Drives - HireFlow Officer Console" />
<c:set var="activeNav" value="officer-drives" />
<c:set var="breadcrumbCurrent" value="Recruitment Drives" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1200px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Directory Header Card -->
    <div class="card" style="margin-bottom: 2rem;">
        <div class="card-body" style="padding: 2rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
            <div>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                    💼 Campus Recruitment Drives
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    Configure corporate placement openings, academic eligibility thresholds, and registration timelines.
                </p>
            </div>
            <div>
                <a href="${pageContext.request.contextPath}/officer/drives/new" class="btn btn-primary" id="btnPublishDrive">
                    + Publish New Drive
                </a>
            </div>
        </div>
    </div>

    <!-- Drives Table Card -->
    <div class="card">
        <div class="card-body" style="padding: 1.75rem;">
            <c:choose>
                <c:when test="${not empty drives}">
                    <div style="overflow-x: auto;">
                        <table style="width: 100%; border-collapse: collapse; font-size: 0.95rem;">
                            <thead>
                                <tr style="border-bottom: 2px solid var(--border-color); text-align: left;">
                                    <th style="padding: 0.75rem 1rem;">Drive ID</th>
                                    <th style="padding: 0.75rem 1rem;">Employer</th>
                                    <th style="padding: 0.75rem 1rem;">Job Title</th>
                                    <th style="padding: 0.75rem 1rem;">Min CGPA</th>
                                    <th style="padding: 0.75rem 1rem;">Branches</th>
                                    <th style="padding: 0.75rem 1rem;">Deadline</th>
                                    <th style="padding: 0.75rem 1rem;">Status</th>
                                    <th style="padding: 0.75rem 1rem; text-align: right;">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="drive" items="${drives}">
                                    <c:set var="isPassed" value="${drive.isDeadlinePassed(java.time.LocalDate.now())}" />
                                    <tr style="border-bottom: 1px solid var(--border-color);" id="drive-row-${drive.id}">
                                        <td style="padding: 0.75rem 1rem; font-weight: 600;">
                                            <code><c:out value="${drive.id}"/></code>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:out value="${companyNames[drive.companyId] != null ? companyNames[drive.companyId] : drive.companyId}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; font-weight: 600; color: var(--text-primary);">
                                            <c:out value="${drive.jobTitle}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:out value="${drive.minimumCgpa}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:forEach var="branch" items="${drive.eligibleBranches}">
                                                <span class="badge" style="background: rgba(37, 99, 235, 0.1); color: var(--primary); margin-right: 0.2rem; font-size: 0.75rem;">
                                                    <c:out value="${branch}"/>
                                                </span>
                                            </c:forEach>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; color: var(--text-muted);">
                                            <c:out value="${drive.applicationDeadline}"/>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:choose>
                                                <c:when test="${drive.closed}">
                                                    <span class="badge badge-closed">MANUALLY CLOSED</span>
                                                </c:when>
                                                <c:when test="${isPassed}">
                                                    <span class="badge badge-closed">EXPIRED</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="badge badge-eligible">ACTIVE</span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; text-align: right; white-space: nowrap;">
                                            <a href="${pageContext.request.contextPath}/officer/drives/${drive.id}/applications"
                                               class="btn btn-primary" style="font-size: 0.8rem; padding: 0.35rem 0.65rem;"
                                               id="viewApplicantsBtn-${drive.id}">
                                                Applicants
                                            </a>
                                            <a href="${pageContext.request.contextPath}/officer/drives/${drive.id}/edit"
                                               class="btn btn-outline" style="font-size: 0.8rem; padding: 0.35rem 0.65rem;"
                                               id="editDriveBtn-${drive.id}">
                                                Edit
                                            </a>
                                            <c:if test="${!drive.closed && !isPassed}">
                                                <form action="${pageContext.request.contextPath}/officer/drives/${drive.id}/close"
                                                      method="POST" style="display: inline;"
                                                      onsubmit="return confirm('Are you sure you want to close this drive? No further applications will be accepted.');">
                                                    <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />
                                                    <button type="submit" class="btn btn-outline"
                                                            style="font-size: 0.8rem; padding: 0.35rem 0.65rem; color: var(--danger); border-color: rgba(239, 68, 68, 0.4);"
                                                            id="closeDriveBtn-${drive.id}">
                                                        Close
                                                    </button>
                                                </form>
                                            </c:if>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:when>
                <c:otherwise>
                    <div style="text-align: center; padding: 3rem 1rem; color: var(--text-muted);">
                        <div style="font-size: 2.5rem; margin-bottom: 1rem;">💼</div>
                        <h3 style="font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin-bottom: 0.5rem;">
                            No Recruitment Drives Configured
                        </h3>
                        <p style="font-size: 0.9rem; margin-bottom: 1.5rem;">
                            Publish your first campus placement drive to accept candidate applications.
                        </p>
                        <a href="${pageContext.request.contextPath}/officer/drives/new" class="btn btn-primary">
                            + Publish New Drive
                        </a>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
