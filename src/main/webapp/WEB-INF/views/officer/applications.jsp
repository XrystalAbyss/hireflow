<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Applicant Review & Shortlisting - HireFlow Officer Console" />
<c:set var="activeNav" value="officer-apps" />
<c:set var="breadcrumbCurrent" value="Candidate Applications" />
<%@ include file="/WEB-INF/views/common/header.jspf" %>

<div style="max-width: 1300px; margin: 2rem auto; padding: 0 1rem;">
    <!-- Header Card -->
    <div class="card" style="margin-bottom: 1.5rem;">
        <div class="card-body" style="padding: 2rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
            <div>
                <h1 style="font-size: 1.75rem; font-weight: 800; color: var(--text-primary); margin: 0 0 0.25rem;">
                    📋 Applicant Reviews &amp; Shortlisting
                </h1>
                <p style="color: var(--text-muted); margin: 0; font-size: 0.875rem;">
                    Examine candidate academic profiles, verify server-evaluated eligibility, and promote qualified students to interview rounds.
                </p>
            </div>
            <div>
                <span class="badge" style="background: var(--bg-hover); color: var(--text-secondary); font-size: 0.85rem; padding: 0.5rem 0.85rem;">
                    Total Applications: <strong><c:out value="${totalCount}"/></strong>
                </span>
            </div>
        </div>
    </div>

    <!-- Filters Bar Card -->
    <div class="card" style="margin-bottom: 1.5rem;">
        <div class="card-body" style="padding: 1.25rem;">
            <form action="${pageContext.request.contextPath}/officer/applications" method="GET" style="display: flex; gap: 1rem; align-items: center; flex-wrap: wrap;">
                <div style="flex: 1; min-width: 200px;">
                    <label for="filterDrive" style="font-size: 0.75rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; display: block; margin-bottom: 0.25rem;">
                        Recruitment Drive
                    </label>
                    <select id="filterDrive" name="driveId" class="form-control" style="font-size: 0.875rem; padding: 0.45rem 0.75rem;">
                        <option value="">-- All Recruitment Drives --</option>
                        <c:forEach var="d" items="${drivesList}">
                            <option value="${d.id}" ${filterDriveId == d.id ? 'selected' : ''}>
                                <c:out value="${d.id}"/>: <c:out value="${d.jobTitle}"/> (<c:out value="${d.companyId}"/>)
                            </option>
                        </c:forEach>
                    </select>
                </div>

                <div style="min-width: 160px;">
                    <label for="filterStatus" style="font-size: 0.75rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; display: block; margin-bottom: 0.25rem;">
                        Lifecycle Status
                    </label>
                    <select id="filterStatus" name="status" class="form-control" style="font-size: 0.875rem; padding: 0.45rem 0.75rem;">
                        <option value="">-- All Statuses --</option>
                        <c:forEach var="st" items="${allStatuses}">
                            <option value="${st.name()}" ${filterStatus == st.name() ? 'selected' : ''}>
                                <c:out value="${st.displayName}"/>
                            </option>
                        </c:forEach>
                    </select>
                </div>

                <div style="flex: 1; min-width: 200px;">
                    <label for="searchQuery" style="font-size: 0.75rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; display: block; margin-bottom: 0.25rem;">
                        Search Candidate / Role
                    </label>
                    <input type="text" id="searchQuery" name="q" value="<c:out value='${searchQuery}'/>"
                           class="form-control" placeholder="Search by name, ID, or job role..." style="font-size: 0.875rem; padding: 0.45rem 0.75rem;" />
                </div>

                <div style="align-self: flex-end; display: flex; gap: 0.5rem;">
                    <button type="submit" class="btn btn-primary" style="font-size: 0.85rem; padding: 0.45rem 1rem;">
                        Filter
                    </button>
                    <a href="${pageContext.request.contextPath}/officer/applications" class="btn btn-outline" style="font-size: 0.85rem; padding: 0.45rem 0.75rem;">
                        Reset
                    </a>
                </div>
            </form>
        </div>
    </div>

    <!-- Applicants Table Card -->
    <div class="card">
        <div class="card-body" style="padding: 1.75rem;">
            <c:choose>
                <c:when test="${not empty applicants}">
                    <div style="overflow-x: auto;">
                        <table style="width: 100%; border-collapse: collapse; font-size: 0.9rem;">
                            <thead>
                                <tr style="border-bottom: 2px solid var(--border-color); text-align: left;">
                                    <th style="padding: 0.75rem 1rem;">App ID</th>
                                    <th style="padding: 0.75rem 1rem;">Student Candidate</th>
                                    <th style="padding: 0.75rem 1rem;">Branch &amp; CGPA</th>
                                    <th style="padding: 0.75rem 1rem;">Drive &amp; Employer</th>
                                    <th style="padding: 0.75rem 1rem;">Eligibility Evaluation</th>
                                    <th style="padding: 0.75rem 1rem;">Status</th>
                                    <th style="padding: 0.75rem 1rem; text-align: right;">Shortlist Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="item" items="${applicants}">
                                    <tr style="border-bottom: 1px solid var(--border-color);" id="applicant-row-${item.applicationId}">
                                        <td style="padding: 0.75rem 1rem; font-weight: 600;">
                                            <code><c:out value="${item.applicationId}"/></code>
                                            <div style="font-size: 0.75rem; color: var(--text-muted);">
                                                <c:out value="${item.formattedAppliedAt}"/>
                                            </div>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <div style="font-weight: 700; color: var(--text-primary);">
                                                <c:out value="${item.studentName}"/>
                                            </div>
                                            <div style="font-size: 0.8rem; color: var(--text-muted);">
                                                Roll No: <code><c:out value="${item.studentId}"/></code>
                                            </div>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <span class="badge" style="background: rgba(37, 99, 235, 0.1); color: var(--primary); font-size: 0.75rem;">
                                                <c:out value="${item.studentBranch}"/>
                                            </span>
                                            <div style="font-size: 0.85rem; font-weight: 600; margin-top: 0.25rem;">
                                                CGPA: <c:out value="${item.formattedCgpa}"/>
                                            </div>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <div style="font-weight: 600; color: var(--text-primary);">
                                                <c:out value="${item.jobTitle}"/>
                                            </div>
                                            <div style="font-size: 0.8rem; color: var(--text-secondary);">
                                                🏢 <c:out value="${item.companyName}"/> (<code><c:out value="${item.driveId}"/></code>)
                                            </div>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:choose>
                                                <c:when test="${item.eligible}">
                                                    <span class="badge badge-eligible">ELIGIBLE</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="badge badge-ineligible" title="<c:out value='${item.eligibilityReasons}'/>">
                                                        NOT ELIGIBLE
                                                    </span>
                                                    <div style="font-size: 0.75rem; color: var(--danger); margin-top: 0.2rem; max-width: 200px;">
                                                        <c:out value="${item.eligibilityReasons}"/>
                                                    </div>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td style="padding: 0.75rem 1rem;">
                                            <c:choose>
                                                <c:when test="${item.status.name() == 'APPLIED'}">
                                                    <span class="badge" style="background: rgba(59, 130, 246, 0.1); color: var(--primary);">APPLIED</span>
                                                </c:when>
                                                <c:when test="${item.status.name() == 'SHORTLISTED'}">
                                                    <span class="badge" style="background: rgba(139, 92, 246, 0.1); color: #8b5cf6;">SHORTLISTED</span>
                                                </c:when>
                                                <c:when test="${item.status.name() == 'SCHEDULED'}">
                                                    <span class="badge" style="background: rgba(16, 185, 129, 0.1); color: var(--success);">SCHEDULED</span>
                                                </c:when>
                                                <c:when test="${item.status.name() == 'SELECTED'}">
                                                    <span class="badge" style="background: rgba(16, 185, 129, 0.2); color: var(--success); font-weight: 700;">SELECTED</span>
                                                </c:when>
                                                <c:when test="${item.status.name() == 'REJECTED'}">
                                                    <span class="badge" style="background: rgba(239, 68, 68, 0.1); color: var(--danger);">REJECTED</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="badge"><c:out value="${item.status.displayName}"/></span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td style="padding: 0.75rem 1rem; text-align: right;">
                                            <c:choose>
                                                <c:when test="${item.canShortlist}">
                                                    <form action="${pageContext.request.contextPath}/officer/applications/${item.applicationId}/shortlist"
                                                          method="POST" style="display: inline;">
                                                        <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>" />
                                                        <input type="hidden" name="returnUrl" value="${pageContext.request.contextPath}/officer/applications?${pageContext.request.queryString}" />
                                                        <button type="submit" class="btn btn-primary"
                                                                style="font-size: 0.8rem; padding: 0.35rem 0.75rem;"
                                                                id="shortlistBtn-${item.applicationId}">
                                                            Shortlist &rarr;
                                                        </button>
                                                    </form>
                                                </c:when>
                                                <c:when test="${item.shortlisted}">
                                                    <span style="font-size: 0.8rem; color: #8b5cf6; font-weight: 600;">
                                                        ✓ Shortlisted
                                                    </span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span style="font-size: 0.8rem; color: var(--text-muted);">
                                                        —
                                                    </span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>

                    <!-- Pagination Controls -->
                    <c:if test="${totalPages > 1}">
                        <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid var(--border-color);">
                            <div style="font-size: 0.85rem; color: var(--text-muted);">
                                Showing page <strong><c:out value="${currentPage}"/></strong> of <strong><c:out value="${totalPages}"/></strong> (<c:out value="${totalCount}"/> total candidates)
                            </div>
                            <div style="display: flex; gap: 0.5rem;">
                                <c:if test="${currentPage > 1}">
                                    <a href="${pageContext.request.contextPath}/officer/applications?driveId=${filterDriveId}&status=${filterStatus}&q=${searchQuery}&page=${currentPage - 1}"
                                       class="btn btn-outline" style="font-size: 0.8rem; padding: 0.35rem 0.75rem;">
                                        &larr; Previous
                                    </a>
                                </c:if>
                                <c:if test="${currentPage < totalPages}">
                                    <a href="${pageContext.request.contextPath}/officer/applications?driveId=${filterDriveId}&status=${filterStatus}&q=${searchQuery}&page=${currentPage + 1}"
                                       class="btn btn-outline" style="font-size: 0.8rem; padding: 0.35rem 0.75rem;">
                                        Next &rarr;
                                    </a>
                                </c:if>
                            </div>
                        </div>
                    </c:if>
                </c:when>
                <c:otherwise>
                    <div style="text-align: center; padding: 3rem 1rem; color: var(--text-muted);">
                        <div style="font-size: 2.5rem; margin-bottom: 1rem;">🔍</div>
                        <h3 style="font-size: 1.15rem; font-weight: 700; color: var(--text-primary); margin-bottom: 0.5rem;">
                            No Candidate Applications Found
                        </h3>
                        <p style="font-size: 0.9rem;">
                            No applications match the current filter or search criteria.
                        </p>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/common/footer.jspf" %>
